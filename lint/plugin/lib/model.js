'use strict';
// Helpers over bpmn-moddle elements with the zeebe extension loaded.

function extensions(node, type) {
  const values = (node.extensionElements && node.extensionElements.values) || [];
  return values.filter((v) => v.$type === type);
}

function extension(node, type) {
  return extensions(node, type)[0];
}

const FEEL_WORDS = new Set([
  'if', 'then', 'else', 'and', 'or', 'not', 'null', 'true', 'false', 'some', 'every', 'in', 'satisfies',
  'for', 'return', 'between', 'instance', 'of', 'function', 'now', 'today', 'duration', 'date', 'time',
  'and', 'string', 'number', 'contains', 'starts', 'with', 'count', 'is', 'defined', 'upper', 'lower', 'case'
]);

// The root variable names a FEEL expression reads: string literals dropped, then identifiers that are not
// FEEL words, not function names (followed by "(") and not path segments after a dot.
function feelVariables(expression) {
  if (typeof expression !== 'string' || !expression.startsWith('=')) return [];
  const body = expression.slice(1).replace(/"(?:[^"\\]|\\.)*"/g, '""');
  const names = new Set();
  const re = /(\.)?\b([A-Za-z_][A-Za-z0-9_]*)\b(\s*\()?/g;
  let m;
  while ((m = re.exec(body)) !== null) {
    if (m[1] || m[3]) continue;
    if (FEEL_WORDS.has(m[2])) continue;
    names.add(m[2]);
  }
  return [...names];
}

function isExpression(value) {
  return typeof value === 'string' && value.startsWith('=');
}

module.exports = { extensions, extension, feelVariables, isExpression };
