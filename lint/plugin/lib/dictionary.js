'use strict';
// Loads models/dictionary/*.yaml once. FBX_DICTIONARY_DIR points the rule tests at a fixture dictionary.
const fs = require('fs');
const path = require('path');
const yaml = require('js-yaml');

let cached;

function load() {
  if (cached) return cached;
  const dir = process.env.FBX_DICTIONARY_DIR || path.resolve(__dirname, '../../../models/dictionary');
  const read = (name, key) => {
    const doc = yaml.load(fs.readFileSync(path.join(dir, name), 'utf8')) || {};
    return doc[key] || {};
  };
  cached = {
    variables: read('variables.yaml', 'variables'),
    jobTypes: read('job-types.yaml', 'job-types'),
    messages: read('messages.yaml', 'messages'),
    errors: read('errors.yaml', 'errors')
  };
  return cached;
}

// Names that look like money or personal data are refused even when listed (05 §5.1).
const FORBIDDEN_NAME = /(amount|balance|iban|fee|rate|price|salary|income|firstname|lastname|fullname|^name$|email|e-mail|phone|msisdn|tckn|emiratesid|nationalid|passport|address|birth)/i;

module.exports = { load, FORBIDDEN_NAME, reset: () => { cached = undefined; } };
