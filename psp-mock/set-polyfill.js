Set.prototype.isSubsetOf = function isSubsetOf(superset) {
  for (const elem of this) {
    if (!superset.has(elem)) {
      return false;
    }
  }
  return true;
}

Set.prototype.union = function union(setB) {
  const _union = new Set(this);
  for (const elem of setB) {
    _union.add(elem);
  }
  return _union;
}

Set.prototype.intersection = function intersection(setB) {
  const _intersection = new Set();
  for (const elem of this) {
    if (setA.has(elem)) {
      _intersection.add(elem);
    }
  }
  return _intersection;
}

Set.prototype.difference = function difference(setB) {
  const _difference = new Set(this);
  for (const elem of setB) {
    _difference.delete(elem);
  }
  return _difference;
}
