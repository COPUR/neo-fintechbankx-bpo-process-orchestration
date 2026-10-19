// The only way the staff web builds DOM: elements and text nodes. Strings always become text, never markup,
// and event handler attributes are refused, so server data cannot inject script (test/static-safety.test.js).

export function el(tag, attrs = {}, ...children) {
  const node = document.createElement(tag);
  for (const [name, value] of Object.entries(attrs)) {
    if (value === undefined || value === null || value === false) continue;
    if (/^on/i.test(name)) throw new Error('event attributes are not allowed; use addEventListener');
    if (name === 'class') node.className = value;
    else node.setAttribute(name, value === true ? '' : String(value));
  }
  for (const child of children.flat()) {
    if (child === null || child === undefined || child === false) continue;
    node.append(child instanceof Node ? child : document.createTextNode(String(child)));
  }
  return node;
}

export function clear(node) {
  while (node.firstChild) node.removeChild(node.firstChild);
}
