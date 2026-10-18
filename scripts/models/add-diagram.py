#!/usr/bin/env python3
"""Writes (or rewrites) the BPMNDiagram of a model with a simple layered layout.

The semantic part of the file is left byte for byte as it is; only the <bpmndi:BPMNDiagram> block is
replaced. The layout is for reading the model in a modeler, not a design: ranks left to right by longest
path from the start event, boundary events on the lower edge of their task, straight edges.
Usage: python3 scripts/models/add-diagram.py models/bpmn/<context>/<process-id>.bpmn ...
"""
import re
import sys
import xml.etree.ElementTree as ET

NS = {'bpmn': 'http://www.omg.org/spec/BPMN/20100524/MODEL'}
SIZES = {'task': (100, 80), 'gateway': (50, 50), 'event': (36, 36)}
TASKS = {'serviceTask', 'userTask', 'callActivity', 'businessRuleTask', 'sendTask', 'receiveTask', 'scriptTask', 'task'}
GATEWAYS = {'exclusiveGateway', 'eventBasedGateway', 'parallelGateway', 'inclusiveGateway'}
EVENTS = {'startEvent', 'endEvent', 'intermediateCatchEvent', 'intermediateThrowEvent'}


def local(tag):
    return tag.split('}', 1)[1]


def layout(path):
    text = open(path, encoding='utf-8').read()
    root = ET.fromstring(text)
    process = root.find('bpmn:process', NS)
    nodes, flows, boundaries = {}, [], []
    for el in process:
        kind = local(el.tag)
        if kind in TASKS:
            nodes[el.get('id')] = 'task'
        elif kind in GATEWAYS:
            nodes[el.get('id')] = 'gateway'
        elif kind in EVENTS:
            nodes[el.get('id')] = 'event'
        elif kind == 'boundaryEvent':
            boundaries.append((el.get('id'), el.get('attachedToRef')))
        elif kind == 'sequenceFlow':
            flows.append((el.get('id'), el.get('sourceRef'), el.get('targetRef')))
    attached = dict(boundaries)
    succ = {}
    for _, s, t in flows:
        succ.setdefault(attached.get(s, s), []).append(t)
    rank = {n: 0 for n in nodes}
    for _ in range(len(nodes)):
        changed = False
        for s, ts in succ.items():
            for t in ts:
                if rank.get(t, 0) < rank.get(s, 0) + 1 <= len(nodes):
                    rank[t] = rank[s] + 1
                    changed = True
        if not changed:
            break
    columns = {}
    for n in nodes:  # document order within a rank keeps the happy path on top
        columns.setdefault(rank[n], []).append(n)
    bounds = {}
    for r, members in columns.items():
        for i, n in enumerate(members):
            w, h = SIZES[nodes[n]]
            cx, cy = 120 + r * 170, 120 + i * 150
            bounds[n] = (cx - w // 2, cy - h // 2, w, h)
    for b, task in boundaries:
        x, y, w, h = bounds[task]
        bounds[b] = (x + w - 50, y + h - 18, 36, 36)

    def centre(n):
        x, y, w, h = bounds[n]
        return x + w // 2, y + h // 2

    out = ['  <bpmndi:BPMNDiagram id="BPMNDiagram_1">',
           f'    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="{process.get("id")}">']
    for n, (x, y, w, h) in bounds.items():
        out.append(f'      <bpmndi:BPMNShape id="{n}_di" bpmnElement="{n}">')
        out.append(f'        <dc:Bounds x="{x}" y="{y}" width="{w}" height="{h}" />')
        out.append('      </bpmndi:BPMNShape>')
    for f, s, t in flows:
        (sx, sy), (tx, ty) = centre(s), centre(t)
        out.append(f'      <bpmndi:BPMNEdge id="{f}_di" bpmnElement="{f}">')
        out.append(f'        <di:waypoint x="{sx}" y="{sy}" />')
        if sy != ty:
            out.append(f'        <di:waypoint x="{sx}" y="{ty}" />')
        out.append(f'        <di:waypoint x="{tx}" y="{ty}" />')
        out.append('      </bpmndi:BPMNEdge>')
    out += ['    </bpmndi:BPMNPlane>', '  </bpmndi:BPMNDiagram>']
    diagram = '\n'.join(out) + '\n'

    text = re.sub(r'  <bpmndi:BPMNDiagram.*?</bpmndi:BPMNDiagram>\n', '', text, flags=re.S)
    text = text.replace('</bpmn:definitions>', diagram + '</bpmn:definitions>')
    for prefix, uri in (('bpmndi', 'http://www.omg.org/spec/BPMN/20100524/DI'),
                        ('dc', 'http://www.omg.org/spec/DD/20100524/DC'),
                        ('di', 'http://www.omg.org/spec/DD/20100524/DI')):
        if f'xmlns:{prefix}=' not in text:
            text = text.replace('<bpmn:definitions ', f'<bpmn:definitions xmlns:{prefix}="{uri}"\n                  ', 1)
    open(path, 'w', encoding='utf-8').write(text)


if __name__ == '__main__':
    for p in sys.argv[1:]:
        layout(p)
