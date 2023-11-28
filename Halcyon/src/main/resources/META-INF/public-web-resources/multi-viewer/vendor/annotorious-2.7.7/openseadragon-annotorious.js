var jE = Object.defineProperty;
var GE = Object.defineProperties;
var qE = Object.getOwnPropertyDescriptors;
var lr = Object.getOwnPropertySymbols;
var Ih = Object.prototype.hasOwnProperty;
var Hh = Object.prototype.propertyIsEnumerable;
var cs = (ye, W, Pe) =>
  W in ye
    ? jE(ye, W, {
        enumerable: true,
        configurable: true,
        writable: true,
        value: Pe,
      })
    : (ye[W] = Pe);
var ue = (ye, W) => {
  for (var Pe in W || (W = {})) {
    if (Ih.call(W, Pe)) {
      cs(ye, Pe, W[Pe]);
    }
  }
  if (lr) {
    for (var Pe of lr(W)) {
      if (Hh.call(W, Pe)) {
        cs(ye, Pe, W[Pe]);
      }
    }
  }
  return ye;
};
var je = (ye, W) => GE(ye, qE(W));
var hs = (ye, W) => {
  var Pe = {};
  for (var ot in ye) {
    if (Ih.call(ye, ot) && W.indexOf(ot) < 0) {
      Pe[ot] = ye[ot];
    }
  }
  if (ye != null && lr) {
    for (var ot of lr(ye)) {
      if (W.indexOf(ot) < 0 && Hh.call(ye, ot)) {
        Pe[ot] = ye[ot];
      }
    }
  }
  return Pe;
};
var P = (ye, W, Pe) => (cs(ye, typeof W != 'symbol' ? W + '' : W, Pe), Pe);
(function (ye, W) {
  if (typeof exports == 'object' && typeof module != 'undefined') {
    module.exports = W();
  } else if (typeof define == 'function' && define.amd) {
    define(W);
  } else {
    ye = typeof globalThis != 'undefined' ? globalThis : ye || self;
    ye.OpenSeadragon = ye.OpenSeadragon || {};
    ye.OpenSeadragon.Annotorious = W();
  }
})(this, function () {
  'use strict';
  function vt(i, t) {
    for (var e in t) {
      i[e] = t[e];
    }
    return i;
  }
  function gs(i) {
    var t = i.parentNode;
    if (t) {
      t.removeChild(i);
    }
  }
  function Ae(i, t, e) {
    var n;
    var r;
    var o;
    var s = {};
    for (o in t) {
      if (o == 'key') {
        n = t[o];
      } else if (o == 'ref') {
        r = t[o];
      } else {
        s[o] = t[o];
      }
    }
    if (arguments.length > 2) {
      s.children = arguments.length > 3 ? ye.call(arguments, 2) : e;
    }
    if (typeof i == 'function' && i.defaultProps != null) {
      for (o in i.defaultProps) {
        if (s[o] === void 0) {
          s[o] = i.defaultProps[o];
        }
      }
    }
    return Sn(i, s, n, r, null);
  }
  function Sn(i, t, e, n, r) {
    var o = {
      type: i,
      props: t,
      key: e,
      ref: n,
      __k: null,
      __: null,
      __b: 0,
      __e: null,
      __d: void 0,
      __c: null,
      __h: null,
      constructor: void 0,
      __v: r == null ? ++Pe : r,
    };
    if (r == null && W.vnode != null) {
      W.vnode(o);
    }
    return o;
  }
  function Xn() {
    return { current: null };
  }
  function Ye(i) {
    return i.children;
  }
  function Oe(i, t) {
    this.props = i;
    this.context = t;
  }
  function qt(i, t) {
    if (t == null) {
      if (i.__) {
        return qt(i.__, i.__.__k.indexOf(i) + 1);
      } else {
        return null;
      }
    }
    for (var e; t < i.__k.length; t++) {
      if ((e = i.__k[t]) != null && e.__e != null) {
        return e.__e;
      }
    }
    if (typeof i.type == 'function') {
      return qt(i);
    } else {
      return null;
    }
  }
  function vs(i) {
    var t;
    var e;
    if ((i = i.__) != null && i.__c != null) {
      i.__e = i.__c.base = null;
      for (t = 0; t < i.__k.length; t++) {
        if ((e = i.__k[t]) != null && e.__e != null) {
          i.__e = i.__c.base = e.__e;
          break;
        }
      }
      return vs(i);
    }
  }
  function ur(i) {
    if (
      (!i.__d && (i.__d = true) && bn.push(i) && !Yn.__r++) ||
      fs !== W.debounceRendering
    ) {
      ((fs = W.debounceRendering) || ds)(Yn);
    }
  }
  function Yn() {
    for (var i; (Yn.__r = bn.length); ) {
      i = bn.sort(function (t, e) {
        return t.__v.__b - e.__v.__b;
      });
      bn = [];
      i.some(function (t) {
        var e;
        var n;
        var r;
        var o;
        var s;
        var a;
        if (t.__d) {
          s = (o = (e = t).__v).__e;
          if ((a = e.__P)) {
            n = [];
            (r = vt({}, o)).__v = o.__v + 1;
            cr(
              a,
              o,
              r,
              e.__n,
              a.ownerSVGElement !== void 0,
              o.__h != null ? [s] : null,
              n,
              s == null ? qt(o) : s,
              o.__h
            );
            xs(n, o);
            if (o.__e != s) {
              vs(o);
            }
          }
        }
      });
    }
  }
  function ys(i, t, e, n, r, o, s, a, l, u) {
    var c;
    var h;
    var d;
    var g;
    var y;
    var x;
    var b;
    var T = (n && n.__k) || ms;
    var f = T.length;
    e.__k = [];
    for (c = 0; c < t.length; c++) {
      if (
        (g = e.__k[c] =
          (g = t[c]) == null || typeof g == 'boolean'
            ? null
            : typeof g == 'string' ||
              typeof g == 'number' ||
              typeof g == 'bigint'
            ? Sn(null, g, null, null, g)
            : Array.isArray(g)
            ? Sn(Ye, { children: g }, null, null, null)
            : g.__b > 0
            ? Sn(g.type, g.props, g.key, null, g.__v)
            : g) != null
      ) {
        g.__ = e;
        g.__b = e.__b + 1;
        if ((d = T[c]) === null || (d && g.key == d.key && g.type === d.type)) {
          T[c] = void 0;
        } else {
          for (h = 0; h < f; h++) {
            if ((d = T[h]) && g.key == d.key && g.type === d.type) {
              T[h] = void 0;
              break;
            }
            d = null;
          }
        }
        cr(i, g, (d = d || qn), r, o, s, a, l, u);
        y = g.__e;
        if ((h = g.ref) && d.ref != h) {
          if (!b) {
            b = [];
          }
          if (d.ref) {
            b.push(d.ref, null, g);
          }
          b.push(h, g.__c || y, g);
        }
        if (y == null) {
          if (l && d.__e == l && l.parentNode != i) {
            l = qt(d);
          }
        } else {
          if (x == null) {
            x = y;
          }
          if (typeof g.type == 'function' && g.__k === d.__k) {
            g.__d = l = ws(g, l, i);
          } else {
            l = bs(i, g, d, T, y, l);
          }
          if (typeof e.type == 'function') {
            e.__d = l;
          }
        }
      }
    }
    e.__e = x;
    for (c = f; c--; ) {
      if (T[c] != null) {
        if (
          typeof e.type == 'function' &&
          T[c].__e != null &&
          T[c].__e == e.__d
        ) {
          e.__d = qt(n, c + 1);
        }
        Cs(T[c], T[c]);
      }
    }
    if (b) {
      for (c = 0; c < b.length; c++) {
        Ts(b[c], b[++c], b[++c]);
      }
    }
  }
  function ws(i, t, e) {
    var n;
    var r = i.__k;
    for (var o = 0; r && o < r.length; o++) {
      if ((n = r[o])) {
        n.__ = i;
        t =
          typeof n.type == 'function' ? ws(n, t, e) : bs(e, n, n, r, n.__e, t);
      }
    }
    return t;
  }
  function lt(i, t) {
    t = t || [];
    if (i != null && typeof i != 'boolean') {
      if (Array.isArray(i)) {
        i.some(function (e) {
          lt(e, t);
        });
      } else {
        t.push(i);
      }
    }
    return t;
  }
  function bs(i, t, e, n, r, o) {
    var s;
    var a;
    var l;
    if (t.__d === void 0) {
      if (e == null || r != o || r.parentNode == null) {
        e: if (o == null || o.parentNode !== i) {
          i.appendChild(r);
          s = null;
        } else {
          a = o;
          for (l = 0; (a = a.nextSibling) && l < n.length; l += 2) {
            if (a == r) {
              break e;
            }
          }
          i.insertBefore(r, o);
          s = o;
        }
      }
    } else {
      s = t.__d;
      t.__d = void 0;
    }
    if (s === void 0) {
      return r.nextSibling;
    } else {
      return s;
    }
  }
  function Vh(i, t, e, n, r) {
    var o;
    for (o in e) {
      if (o !== 'children' && o !== 'key' && !(o in t)) {
        Zn(i, o, null, e[o], n);
      }
    }
    for (o in t) {
      if (
        (!r || typeof t[o] == 'function') &&
        o !== 'children' &&
        o !== 'key' &&
        o !== 'value' &&
        o !== 'checked' &&
        e[o] !== t[o]
      ) {
        Zn(i, o, t[o], e[o], n);
      }
    }
  }
  function Ss(i, t, e) {
    if (t[0] === '-') {
      i.setProperty(t, e);
    } else {
      i[t] = e == null ? '' : typeof e != 'number' || zh.test(t) ? e : e + 'px';
    }
  }
  function Zn(i, t, e, n, r) {
    var o;
    e: if (t === 'style') {
      if (typeof e == 'string') {
        i.style.cssText = e;
      } else {
        if (typeof n == 'string') {
          i.style.cssText = n = '';
        }
        if (n) {
          for (t in n) {
            if (!e || !(t in e)) {
              Ss(i.style, t, '');
            }
          }
        }
        if (e) {
          for (t in e) {
            if (!n || e[t] !== n[t]) {
              Ss(i.style, t, e[t]);
            }
          }
        }
      }
    } else if (t[0] === 'o' && t[1] === 'n') {
      o = t !== (t = t.replace(/Capture$/, ''));
      t = t.toLowerCase() in i ? t.toLowerCase().slice(2) : t.slice(2);
      if (!i.l) {
        i.l = {};
      }
      i.l[t + o] = e;
      if (e) {
        if (!n) {
          i.addEventListener(t, o ? _s : Es, o);
        }
      } else {
        i.removeEventListener(t, o ? _s : Es, o);
      }
    } else if (t !== 'dangerouslySetInnerHTML') {
      if (r) {
        t = t.replace(/xlink[H:h]/, 'h').replace(/sName$/, 's');
      } else if (
        t !== 'href' &&
        t !== 'list' &&
        t !== 'form' &&
        t !== 'tabIndex' &&
        t !== 'download' &&
        t in i
      ) {
        try {
          i[t] = e == null ? '' : e;
          break e;
        } catch {}
      }
      if (typeof e != 'function') {
        if (e != null && (e !== false || (t[0] === 'a' && t[1] === 'r'))) {
          i.setAttribute(t, e);
        } else {
          i.removeAttribute(t);
        }
      }
    }
  }
  function Es(i) {
    this.l[i.type + false](W.event ? W.event(i) : i);
  }
  function _s(i) {
    this.l[i.type + true](W.event ? W.event(i) : i);
  }
  function cr(i, t, e, n, r, o, s, a, l) {
    var u;
    var c;
    var h;
    var d;
    var g;
    var y;
    var x;
    var b;
    var T;
    var f;
    var E;
    var A = t.type;
    if (t.constructor !== void 0) {
      return null;
    }
    if (e.__h != null) {
      l = e.__h;
      a = t.__e = e.__e;
      t.__h = null;
      o = [a];
    }
    if ((u = W.__b)) {
      u(t);
    }
    try {
      e: if (typeof A == 'function') {
        b = t.props;
        T = (u = A.contextType) && n[u.__c];
        f = u ? (T ? T.props.value : u.__) : n;
        if (e.__c) {
          x = (c = t.__c = e.__c).__ = c.__E;
        } else {
          if ('prototype' in A && A.prototype.render) {
            t.__c = c = new A(b, f);
          } else {
            t.__c = c = new Oe(b, f);
            c.constructor = A;
            c.render = Wh;
          }
          if (T) {
            T.sub(c);
          }
          c.props = b;
          if (!c.state) {
            c.state = {};
          }
          c.context = f;
          c.__n = n;
          h = c.__d = true;
          c.__h = [];
        }
        if (c.__s == null) {
          c.__s = c.state;
        }
        if (A.getDerivedStateFromProps != null) {
          if (c.__s == c.state) {
            c.__s = vt({}, c.__s);
          }
          vt(c.__s, A.getDerivedStateFromProps(b, c.__s));
        }
        d = c.props;
        g = c.state;
        if (h) {
          if (
            A.getDerivedStateFromProps == null &&
            c.componentWillMount != null
          ) {
            c.componentWillMount();
          }
          if (c.componentDidMount != null) {
            c.__h.push(c.componentDidMount);
          }
        } else {
          if (
            A.getDerivedStateFromProps == null &&
            b !== d &&
            c.componentWillReceiveProps != null
          ) {
            c.componentWillReceiveProps(b, f);
          }
          if (
            (!c.__e &&
              c.shouldComponentUpdate != null &&
              c.shouldComponentUpdate(b, c.__s, f) === false) ||
            t.__v === e.__v
          ) {
            c.props = b;
            c.state = c.__s;
            if (t.__v !== e.__v) {
              c.__d = false;
            }
            c.__v = t;
            t.__e = e.__e;
            t.__k = e.__k;
            t.__k.forEach(function (C) {
              if (C) {
                C.__ = t;
              }
            });
            if (c.__h.length) {
              s.push(c);
            }
            break e;
          }
          if (c.componentWillUpdate != null) {
            c.componentWillUpdate(b, c.__s, f);
          }
          if (c.componentDidUpdate != null) {
            c.__h.push(function () {
              c.componentDidUpdate(d, g, y);
            });
          }
        }
        c.context = f;
        c.props = b;
        c.state = c.__s;
        if ((u = W.__r)) {
          u(t);
        }
        c.__d = false;
        c.__v = t;
        c.__P = i;
        u = c.render(c.props, c.state, c.context);
        c.state = c.__s;
        if (c.getChildContext != null) {
          n = vt(vt({}, n), c.getChildContext());
        }
        if (!h && c.getSnapshotBeforeUpdate != null) {
          y = c.getSnapshotBeforeUpdate(d, g);
        }
        E = u != null && u.type === Ye && u.key == null ? u.props.children : u;
        ys(i, Array.isArray(E) ? E : [E], t, e, n, r, o, s, a, l);
        c.base = t.__e;
        t.__h = null;
        if (c.__h.length) {
          s.push(c);
        }
        if (x) {
          c.__E = c.__ = null;
        }
        c.__e = false;
      } else if (o == null && t.__v === e.__v) {
        t.__k = e.__k;
        t.__e = e.__e;
      } else {
        t.__e = Uh(e.__e, t, e, n, r, o, s, l);
      }
      if ((u = W.diffed)) {
        u(t);
      }
    } catch (C) {
      t.__v = null;
      if (l || o != null) {
        t.__e = a;
        t.__h = !!l;
        o[o.indexOf(a)] = null;
      }
      W.__e(C, t, e);
    }
  }
  function xs(i, t) {
    if (W.__c) {
      W.__c(t, i);
    }
    i.some(function (e) {
      try {
        i = e.__h;
        e.__h = [];
        i.some(function (n) {
          n.call(e);
        });
      } catch (n) {
        W.__e(n, e.__v);
      }
    });
  }
  function Uh(i, t, e, n, r, o, s, a) {
    var l;
    var u;
    var c;
    var h = e.props;
    var d = t.props;
    var g = t.type;
    var y = 0;
    if (g === 'svg') {
      r = true;
    }
    if (o != null) {
    }
    if (i == null) {
      if (g === null) {
        return document.createTextNode(d);
      }
      i = r
        ? document.createElementNS('http://www.w3.org/2000/svg', g)
        : document.createElement(g, d.is && d);
      o = null;
      a = false;
    }
    if (g === null) {
      if (h !== d && (!a || i.data !== d)) {
        i.data = d;
      }
    } else {
      o = o && ye.call(i.childNodes);
      u = (h = e.props || qn).dangerouslySetInnerHTML;
      c = d.dangerouslySetInnerHTML;
      if (!a) {
        if (o != null) {
          h = {};
          for (y = 0; y < i.attributes.length; y++) {
            h[i.attributes[y].name] = i.attributes[y].value;
          }
        }
        if (c || u) {
          if (
            !c ||
            ((!u || c.__html != u.__html) && c.__html !== i.innerHTML)
          ) {
            i.innerHTML = (c && c.__html) || '';
          }
        }
      }
      Vh(i, d, h, r, a);
      if (c) {
        t.__k = [];
      } else if (
        ((y = t.props.children),
        ys(
          i,
          Array.isArray(y) ? y : [y],
          t,
          e,
          n,
          r && g !== 'foreignObject',
          o,
          s,
          o ? o[0] : e.__k && qt(e, 0),
          a
        ),
        o != null)
      ) {
        for (y = o.length; y--; ) {
          if (o[y] != null) {
            gs(o[y]);
          }
        }
      }
      if (!a) {
        if (
          'value' in d &&
          (y = d.value) !== void 0 &&
          (y !== h.value || y !== i.value || (g === 'progress' && !y))
        ) {
          Zn(i, 'value', y, h.value, false);
        }
        if ('checked' in d && (y = d.checked) !== void 0 && y !== i.checked) {
          Zn(i, 'checked', y, h.checked, false);
        }
      }
    }
    return i;
  }
  function Ts(i, t, e) {
    try {
      if (typeof i == 'function') {
        i(t);
      } else {
        i.current = t;
      }
    } catch (n) {
      W.__e(n, e);
    }
  }
  function Cs(i, t, e) {
    var n;
    var r;
    if (W.unmount) {
      W.unmount(i);
    }
    if ((n = i.ref)) {
      if (!n.current || n.current === i.__e) {
        Ts(n, null, t);
      }
    }
    if ((n = i.__c) != null) {
      if (n.componentWillUnmount) {
        try {
          n.componentWillUnmount();
        } catch (o) {
          W.__e(o, t);
        }
      }
      n.base = n.__P = null;
    }
    if ((n = i.__k)) {
      for (r = 0; r < n.length; r++) {
        if (n[r]) {
          Cs(n[r], t, typeof i.type != 'function');
        }
      }
    }
    if (!e && i.__e != null) {
      gs(i.__e);
    }
    i.__e = i.__d = void 0;
  }
  function Wh(i, t, e) {
    return this.constructor(i, e);
  }
  function Xt(i, t, e) {
    var n;
    if (W.__) {
      W.__(i, t);
    }
    var r = (n = typeof e == 'function') ? null : (e && e.__k) || t.__k;
    var o = [];
    cr(
      t,
      (i = ((!n && e) || t).__k = Ae(Ye, null, [i])),
      r || qn,
      qn,
      t.ownerSVGElement !== void 0,
      !n && e ? [e] : r ? null : t.firstChild ? ye.call(t.childNodes) : null,
      o,
      !n && e ? e : r ? r.__e : t.firstChild,
      n
    );
    xs(o, i);
  }
  function hr(i, t) {
    Xt(i, t, hr);
  }
  function Ps(i, t, e) {
    var n;
    var r;
    var o;
    var s = vt({}, i.props);
    for (o in t) {
      if (o == 'key') {
        n = t[o];
      } else if (o == 'ref') {
        r = t[o];
      } else {
        s[o] = t[o];
      }
    }
    if (arguments.length > 2) {
      s.children = arguments.length > 3 ? ye.call(arguments, 2) : e;
    }
    return Sn(i.type, s, n || i.key, r || i.ref, null);
  }
  function Yt(i, t) {
    var e = {
      __c: (t = '__cC' + ps++),
      __: i,
      Consumer: function (n, r) {
        return n.children(r);
      },
      Provider: function (n) {
        var r;
        var o;
        if (!this.getChildContext) {
          r = [];
          (o = {})[t] = this;
          this.getChildContext = function () {
            return o;
          };
          this.shouldComponentUpdate = function (s) {
            if (this.props.value !== s.value) {
              r.some(ur);
            }
          };
          this.sub = function (s) {
            r.push(s);
            var a = s.componentWillUnmount;
            s.componentWillUnmount = function () {
              r.splice(r.indexOf(s), 1);
              if (a) {
                a.call(s);
              }
            };
          };
        }
        return n.children;
      },
    };
    return (e.Provider.__ = e.Consumer.contextType = e);
  }
  function Kt(i, t) {
    if (W.__h) {
      W.__h(Be, i, Zt || t);
    }
    Zt = 0;
    var e = Be.__H || (Be.__H = { __: [], __h: [] });
    if (i >= e.__.length) {
      e.__.push({});
    }
    return e.__[i];
  }
  function yt(i) {
    Zt = 1;
    return fr(Ns, i);
  }
  function fr(i, t, e) {
    var n = Kt(Lt++, 2);
    n.t = i;
    if (!n.__c) {
      n.__ = [
        e ? e(t) : Ns(void 0, t),
        function (r) {
          var o = n.t(n.__[0], r);
          if (n.__[0] !== o) {
            n.__ = [o, n.__[1]];
            n.__c.setState({});
          }
        },
      ];
      n.__c = Be;
    }
    return n.__;
  }
  function kt(i, t) {
    var e = Kt(Lt++, 3);
    if (!W.__s && gr(e.__H, t)) {
      e.__ = i;
      e.__H = t;
      Be.__H.__h.push(e);
    }
  }
  function pr(i, t) {
    var e = Kt(Lt++, 4);
    if (!W.__s && gr(e.__H, t)) {
      e.__ = i;
      e.__H = t;
      Be.__h.push(e);
    }
  }
  function ut(i) {
    Zt = 5;
    return wt(function () {
      return { current: i };
    }, []);
  }
  function Ls(i, t, e) {
    Zt = 6;
    pr(
      function () {
        if (typeof i == 'function') {
          i(t());
        } else if (i) {
          i.current = t();
        }
      },
      e == null ? e : e.concat(i)
    );
  }
  function wt(i, t) {
    var e = Kt(Lt++, 7);
    if (gr(e.__H, t)) {
      e.__ = i();
      e.__H = t;
      e.__h = i;
    }
    return e.__;
  }
  function ct(i, t) {
    Zt = 8;
    return wt(function () {
      return i;
    }, t);
  }
  function En(i) {
    var t = Be.context[i.__c];
    var e = Kt(Lt++, 9);
    e.c = i;
    if (t) {
      if (e.__ == null) {
        e.__ = true;
        t.sub(Be);
      }
      return t.props.value;
    } else {
      return i.__;
    }
  }
  function ks(i, t) {
    if (W.useDebugValue) {
      W.useDebugValue(t ? t(i) : i);
    }
  }
  function Gh(i) {
    var t = Kt(Lt++, 10);
    var e = yt();
    t.__ = i;
    if (!Be.componentDidCatch) {
      Be.componentDidCatch = function (n) {
        if (t.__) {
          t.__(n);
        }
        e[1](n);
      };
    }
    return [
      e[0],
      function () {
        e[1](void 0);
      },
    ];
  }
  function qh() {
    var i;
    for (
      dr.sort(function (t, e) {
        return t.__v.__b - e.__v.__b;
      });
      (i = dr.pop());

    ) {
      if (i.__P) {
        try {
          i.__H.__h.forEach(Kn);
          i.__H.__h.forEach(mr);
          i.__H.__h = [];
        } catch (t) {
          i.__H.__h = [];
          W.__e(t, i.__v);
        }
      }
    }
  }
  function Kn(i) {
    var t = Be;
    var e = i.__c;
    if (typeof e == 'function') {
      i.__c = void 0;
      e();
    }
    Be = t;
  }
  function mr(i) {
    var t = Be;
    i.__c = i.__();
    Be = t;
  }
  function gr(i, t) {
    return (
      !i ||
      i.length !== t.length ||
      t.some(function (e, n) {
        return e !== i[n];
      })
    );
  }
  function Ns(i, t) {
    if (typeof t == 'function') {
      return t(i);
    } else {
      return t;
    }
  }
  function Is(i, t) {
    for (var e in t) {
      i[e] = t[e];
    }
    return i;
  }
  function vr(i, t) {
    for (var e in i) {
      if (e !== '__source' && !(e in t)) {
        return true;
      }
    }
    for (var n in t) {
      if (n !== '__source' && i[n] !== t[n]) {
        return true;
      }
    }
    return false;
  }
  function _n(i) {
    this.props = i;
  }
  function Hs(i, t) {
    function e(r) {
      var o = this.props.ref;
      var s = o == r.ref;
      if (!s && o) {
        if (o.call) {
          o(null);
        } else {
          o.current = null;
        }
      }
      if (t) {
        return !t(this.props, r) || !s;
      } else {
        return vr(this.props, r);
      }
    }
    function n(r) {
      this.shouldComponentUpdate = e;
      return Ae(i, r);
    }
    n.displayName = 'Memo(' + (i.displayName || i.name) + ')';
    n.prototype.isReactComponent = true;
    n.__f = true;
    return n;
  }
  function yr(i) {
    function t(e, n) {
      var r = Is({}, e);
      delete r.ref;
      return i(
        r,
        (n = e.ref || n) && (typeof n != 'object' || 'current' in n) ? n : null
      );
    }
    t.$$typeof = Xh;
    t.render = t;
    t.prototype.isReactComponent = t.__f = true;
    t.displayName = 'ForwardRef(' + (i.displayName || i.name) + ')';
    return t;
  }
  function xn() {
    this.__u = 0;
    this.t = null;
    this.__b = null;
  }
  function js(i) {
    var t = i.__.__c;
    return t && t.__e && t.__e(i);
  }
  function Gs(i) {
    function r(o) {
      if (!t) {
        (t = i()).then(
          function (s) {
            e = s.default || s;
          },
          function (s) {
            n = s;
          }
        );
      }
      if (n) {
        throw n;
      }
      if (!e) {
        throw t;
      }
      return Ae(e, o);
    }
    var t;
    var e;
    var n;
    r.displayName = 'Lazy';
    r.__f = true;
    return r;
  }
  function Jt() {
    this.u = null;
    this.o = null;
  }
  function Zh(i) {
    this.getChildContext = function () {
      return i.context;
    };
    return i.children;
  }
  function Kh(i) {
    var t = this;
    var e = i.i;
    t.componentWillUnmount = function () {
      Xt(null, t.l);
      t.l = null;
      t.i = null;
    };
    if (t.i && t.i !== e) {
      t.componentWillUnmount();
    }
    if (i.__v) {
      if (!t.l) {
        t.i = e;
        t.l = {
          nodeType: 1,
          parentNode: e,
          childNodes: [],
          appendChild: function (n) {
            this.childNodes.push(n);
            t.i.appendChild(n);
          },
          insertBefore: function (n, r) {
            this.childNodes.push(n);
            t.i.appendChild(n);
          },
          removeChild: function (n) {
            this.childNodes.splice(this.childNodes.indexOf(n) >>> 1, 1);
            t.i.removeChild(n);
          },
        };
      }
      Xt(Ae(Zh, { context: t.context }, i.__v), t.l);
    } else if (t.l) {
      t.componentWillUnmount();
    }
  }
  function wr(i, t) {
    return Ae(Kh, { __v: i, i: t });
  }
  function Ys(i, t, e) {
    if (t.__k == null) {
      t.textContent = '';
    }
    Xt(i, t);
    if (typeof e == 'function') {
      e();
    }
    if (i) {
      return i.__c;
    } else {
      return null;
    }
  }
  function Zs(i, t, e) {
    hr(i, t);
    if (typeof e == 'function') {
      e();
    }
    if (i) {
      return i.__c;
    } else {
      return null;
    }
  }
  function ed() {}
  function td() {
    return this.cancelBubble;
  }
  function nd() {
    return this.defaultPrevented;
  }
  function na(i) {
    return Ae.bind(null, i);
  }
  function br(i) {
    return !!i && i.$$typeof === Xs;
  }
  function ia(i) {
    if (br(i)) {
      return Ps.apply(null, arguments);
    } else {
      return i;
    }
  }
  function ra(i) {
    if (!i.__k) {
      return false;
    }
    Xt(null, i);
    return true;
  }
  function oa(i) {
    return (i && (i.base || (i.nodeType === 1 && i))) || null;
  }
  function sd(i) {
    if (
      i &&
      i.__esModule &&
      Object.prototype.hasOwnProperty.call(i, 'default')
    ) {
      return i.default;
    } else {
      return i;
    }
  }
  function Jn(i) {
    if (i.__esModule) {
      return i;
    }
    var t = Object.defineProperty({}, '__esModule', { value: true });
    Object.keys(i).forEach(function (e) {
      var n = Object.getOwnPropertyDescriptor(i, e);
      Object.defineProperty(
        t,
        e,
        n.get
          ? n
          : {
              enumerable: true,
              get: function () {
                return i[e];
              },
            }
      );
    });
    return t;
  }
  function Er() {}
  function ua() {}
  function ca() {}
  function ha(i) {
    var t;
    var e;
    var n = '';
    if (typeof i == 'string' || typeof i == 'number') {
      n += i;
    } else if (typeof i == 'object') {
      if (Array.isArray(i)) {
        for (t = 0; t < i.length; t++) {
          if (i[t] && (e = ha(i[t]))) {
            if (n) {
              n += ' ';
            }
            n += e;
          }
        }
      } else {
        for (t in i) {
          if (i[t]) {
            if (n) {
              n += ' ';
            }
            n += t;
          }
        }
      }
    }
    return n;
  }
  function hd() {
    var i = 0;
    var t;
    var e;
    for (var n = ''; i < arguments.length; ) {
      if ((t = arguments[i++]) && (e = ha(t))) {
        if (n) {
          n += ' ';
        }
        n += e;
      }
    }
    return n;
  }
  function pd(i, t) {
    var e = 0;
    for (var n = i.length; e < n; e++) {
      if (t.apply(t, [i[e], e, i])) {
        return i[e];
      }
    }
  }
  function md(i) {
    return (
      typeof i == 'function' ||
      Object.prototype.toString.call(i) === '[object Function]'
    );
  }
  function gd(i) {
    return typeof i == 'number' && !isNaN(i);
  }
  function vd(i) {
    return parseInt(i, 10);
  }
  function yd(i, t, e) {
    if (i[t]) {
      return new Error(
        'Invalid prop '
          .concat(t, ' passed to ')
          .concat(e, ' - do not set this, set it on the child.')
      );
    }
  }
  function da() {
    var i;
    var t;
    var e =
      arguments.length > 0 && arguments[0] !== void 0
        ? arguments[0]
        : 'transform';
    if (typeof window == 'undefined') {
      return '';
    }
    var n =
      (i = window.document) === null ||
      i === void 0 ||
      (t = i.documentElement) === null ||
      t === void 0
        ? void 0
        : t.style;
    if (!n || e in n) {
      return '';
    }
    for (var r = 0; r < _r.length; r++) {
      if (fa(e, _r[r]) in n) {
        return _r[r];
      }
    }
    return '';
  }
  function fa(i, t) {
    if (t) {
      return ''.concat(t).concat(bd(i));
    } else {
      return i;
    }
  }
  function wd(i, t) {
    if (t) {
      return '-'.concat(t.toLowerCase(), '-').concat(i);
    } else {
      return i;
    }
  }
  function bd(i) {
    var t = '';
    var e = true;
    for (var n = 0; n < i.length; n++) {
      if (e) {
        t += i[n].toUpperCase();
        e = false;
      } else if (i[n] === '-') {
        e = true;
      } else {
        t += i[n];
      }
    }
    return t;
  }
  function ei(i) {
    if (typeof Symbol == 'function' && typeof Symbol.iterator == 'symbol') {
      ei = function (e) {
        return typeof e;
      };
    } else {
      ei = function (e) {
        if (
          e &&
          typeof Symbol == 'function' &&
          e.constructor === Symbol &&
          e !== Symbol.prototype
        ) {
          return 'symbol';
        } else {
          return typeof e;
        }
      };
    }
    return ei(i);
  }
  function ma(i) {
    if (typeof WeakMap != 'function') {
      return null;
    }
    var t = new WeakMap();
    var e = new WeakMap();
    return (ma = function (r) {
      if (r) {
        return e;
      } else {
        return t;
      }
    })(i);
  }
  function Ed(i, t) {
    if (!t && i && i.__esModule) {
      return i;
    }
    if (i === null || (ei(i) !== 'object' && typeof i != 'function')) {
      return { default: i };
    }
    var e = ma(t);
    if (e && e.has(i)) {
      return e.get(i);
    }
    var n = {};
    var r = Object.defineProperty && Object.getOwnPropertyDescriptor;
    for (var o in i) {
      if (o !== 'default' && Object.prototype.hasOwnProperty.call(i, o)) {
        var s = r ? Object.getOwnPropertyDescriptor(i, o) : null;
        if (s && (s.get || s.set)) {
          Object.defineProperty(n, o, s);
        } else {
          n[o] = i[o];
        }
      }
    }
    n.default = i;
    if (e) {
      e.set(i, n);
    }
    return n;
  }
  function ga(i, t) {
    var e = Object.keys(i);
    if (Object.getOwnPropertySymbols) {
      var n = Object.getOwnPropertySymbols(i);
      if (t) {
        n = n.filter(function (r) {
          return Object.getOwnPropertyDescriptor(i, r).enumerable;
        });
      }
      e.push.apply(e, n);
    }
    return e;
  }
  function va(i) {
    for (var t = 1; t < arguments.length; t++) {
      var e = arguments[t] != null ? arguments[t] : {};
      if (t % 2) {
        ga(Object(e), true).forEach(function (n) {
          ya(i, n, e[n]);
        });
      } else if (Object.getOwnPropertyDescriptors) {
        Object.defineProperties(i, Object.getOwnPropertyDescriptors(e));
      } else {
        ga(Object(e)).forEach(function (n) {
          Object.defineProperty(i, n, Object.getOwnPropertyDescriptor(e, n));
        });
      }
    }
    return i;
  }
  function ya(i, t, e) {
    if (t in i) {
      Object.defineProperty(i, t, {
        value: e,
        enumerable: true,
        configurable: true,
        writable: true,
      });
    } else {
      i[t] = e;
    }
    return i;
  }
  function wa(i, t) {
    if (!ti) {
      ti = Ze.findInArray(
        [
          'matches',
          'webkitMatchesSelector',
          'mozMatchesSelector',
          'msMatchesSelector',
          'oMatchesSelector',
        ],
        function (e) {
          return Ze.isFunction(i[e]);
        }
      );
    }
    if (Ze.isFunction(i[ti])) {
      return i[ti](t);
    } else {
      return false;
    }
  }
  function _d(i, t, e) {
    var n = i;
    do {
      if (wa(n, t)) {
        return true;
      }
      if (n === e) {
        return false;
      }
      n = n.parentNode;
    } while (n);
    return false;
  }
  function xd(i, t, e, n) {
    if (i) {
      var r = va({ capture: true }, n);
      if (i.addEventListener) {
        i.addEventListener(t, e, r);
      } else if (i.attachEvent) {
        i.attachEvent('on' + t, e);
      } else {
        i['on' + t] = e;
      }
    }
  }
  function Td(i, t, e, n) {
    if (i) {
      var r = va({ capture: true }, n);
      if (i.removeEventListener) {
        i.removeEventListener(t, e, r);
      } else if (i.detachEvent) {
        i.detachEvent('on' + t, e);
      } else {
        i['on' + t] = null;
      }
    }
  }
  function Cd(i) {
    var t = i.clientHeight;
    var e = i.ownerDocument.defaultView.getComputedStyle(i);
    t += Ze.int(e.borderTopWidth);
    t += Ze.int(e.borderBottomWidth);
    return t;
  }
  function Pd(i) {
    var t = i.clientWidth;
    var e = i.ownerDocument.defaultView.getComputedStyle(i);
    t += Ze.int(e.borderLeftWidth);
    t += Ze.int(e.borderRightWidth);
    return t;
  }
  function Ad(i) {
    var t = i.clientHeight;
    var e = i.ownerDocument.defaultView.getComputedStyle(i);
    t -= Ze.int(e.paddingTop);
    t -= Ze.int(e.paddingBottom);
    return t;
  }
  function Od(i) {
    var t = i.clientWidth;
    var e = i.ownerDocument.defaultView.getComputedStyle(i);
    t -= Ze.int(e.paddingLeft);
    t -= Ze.int(e.paddingRight);
    return t;
  }
  function Dd(i, t, e) {
    var n = t === t.ownerDocument.body;
    var r = n ? { left: 0, top: 0 } : t.getBoundingClientRect();
    var o = (i.clientX + t.scrollLeft - r.left) / e;
    var s = (i.clientY + t.scrollTop - r.top) / e;
    return { x: o, y: s };
  }
  function Rd(i, t) {
    var e = xr(i, t, 'px');
    return ya({}, pa.browserPrefixToKey('transform', pa.default), e);
  }
  function Md(i, t) {
    var e = xr(i, t, '');
    return e;
  }
  function xr(i, t, e) {
    var n = i.x;
    var r = i.y;
    var o = 'translate('.concat(n).concat(e, ',').concat(r).concat(e, ')');
    if (t) {
      var s = ''.concat(typeof t.x == 'string' ? t.x : t.x + e);
      var a = ''.concat(typeof t.y == 'string' ? t.y : t.y + e);
      o = 'translate('.concat(s, ', ').concat(a, ')') + o;
    }
    return o;
  }
  function Fd(i, t) {
    return (
      (i.targetTouches &&
        Ze.findInArray(i.targetTouches, function (e) {
          return t === e.identifier;
        })) ||
      (i.changedTouches &&
        Ze.findInArray(i.changedTouches, function (e) {
          return t === e.identifier;
        }))
    );
  }
  function Ld(i) {
    if (i.targetTouches && i.targetTouches[0]) {
      return i.targetTouches[0].identifier;
    }
    if (i.changedTouches && i.changedTouches[0]) {
      return i.changedTouches[0].identifier;
    }
  }
  function kd(i) {
    if (i) {
      var t = i.getElementById('react-draggable-style-el');
      if (!t) {
        t = i.createElement('style');
        t.type = 'text/css';
        t.id = 'react-draggable-style-el';
        t.innerHTML = `.react-draggable-transparent-selection *::-moz-selection {all: inherit;}
`;
        t.innerHTML += `.react-draggable-transparent-selection *::selection {all: inherit;}
`;
        i.getElementsByTagName('head')[0].appendChild(t);
      }
      if (i.body) {
        ba(i.body, 'react-draggable-transparent-selection');
      }
    }
  }
  function Bd(i) {
    if (i) {
      try {
        if (i.body) {
          Sa(i.body, 'react-draggable-transparent-selection');
        }
        if (i.selection) {
          i.selection.empty();
        } else {
          var t = (i.defaultView || window).getSelection();
          if (t && t.type !== 'Caret') {
            t.removeAllRanges();
          }
        }
      } catch {}
    }
  }
  function ba(i, t) {
    if (i.classList) {
      i.classList.add(t);
    } else if (
      !i.className.match(new RegExp('(?:^|\\s)'.concat(t, '(?!\\S)')))
    ) {
      i.className += ' '.concat(t);
    }
  }
  function Sa(i, t) {
    if (i.classList) {
      i.classList.remove(t);
    } else {
      i.className = i.className.replace(
        new RegExp('(?:^|\\s)'.concat(t, '(?!\\S)'), 'g'),
        ''
      );
    }
  }
  function Nd(i, t, e) {
    if (!i.props.bounds) {
      return [t, e];
    }
    var n = i.props.bounds;
    n = typeof n == 'string' ? n : jd(n);
    var r = Tr(i);
    if (typeof n == 'string') {
      var o = r.ownerDocument;
      var s = o.defaultView;
      var a;
      if (n === 'parent') {
        a = r.parentNode;
      } else {
        a = o.querySelector(n);
      }
      if (!(a instanceof s.HTMLElement)) {
        throw new Error(
          'Bounds selector "' + n + '" could not find an element.'
        );
      }
      var l = a;
      var u = s.getComputedStyle(r);
      var c = s.getComputedStyle(l);
      n = {
        left: -r.offsetLeft + Ke.int(c.paddingLeft) + Ke.int(u.marginLeft),
        top: -r.offsetTop + Ke.int(c.paddingTop) + Ke.int(u.marginTop),
        right:
          $t.innerWidth(l) -
          $t.outerWidth(r) -
          r.offsetLeft +
          Ke.int(c.paddingRight) -
          Ke.int(u.marginRight),
        bottom:
          $t.innerHeight(l) -
          $t.outerHeight(r) -
          r.offsetTop +
          Ke.int(c.paddingBottom) -
          Ke.int(u.marginBottom),
      };
    }
    if (Ke.isNum(n.right)) {
      t = Math.min(t, n.right);
    }
    if (Ke.isNum(n.bottom)) {
      e = Math.min(e, n.bottom);
    }
    if (Ke.isNum(n.left)) {
      t = Math.max(t, n.left);
    }
    if (Ke.isNum(n.top)) {
      e = Math.max(e, n.top);
    }
    return [t, e];
  }
  function Id(i, t, e) {
    var n = Math.round(t / i[0]) * i[0];
    var r = Math.round(e / i[1]) * i[1];
    return [n, r];
  }
  function Hd(i) {
    return i.props.axis === 'both' || i.props.axis === 'x';
  }
  function zd(i) {
    return i.props.axis === 'both' || i.props.axis === 'y';
  }
  function Vd(i, t, e) {
    var n = typeof t == 'number' ? $t.getTouch(i, t) : null;
    if (typeof t == 'number' && !n) {
      return null;
    }
    var r = Tr(e);
    var o = e.props.offsetParent || r.offsetParent || r.ownerDocument.body;
    return $t.offsetXYFromParent(n || i, o, e.props.scale);
  }
  function Ud(i, t, e) {
    var n = i.state;
    var r = !Ke.isNum(n.lastX);
    var o = Tr(i);
    if (r) {
      return { node: o, deltaX: 0, deltaY: 0, lastX: t, lastY: e, x: t, y: e };
    } else {
      return {
        node: o,
        deltaX: t - n.lastX,
        deltaY: e - n.lastY,
        lastX: n.lastX,
        lastY: n.lastY,
        x: t,
        y: e,
      };
    }
  }
  function Wd(i, t) {
    var e = i.props.scale;
    return {
      node: t.node,
      x: i.state.x + t.deltaX / e,
      y: i.state.y + t.deltaY / e,
      deltaX: t.deltaX / e,
      deltaY: t.deltaY / e,
      lastX: i.state.x,
      lastY: i.state.y,
    };
  }
  function jd(i) {
    return { left: i.left, top: i.top, right: i.right, bottom: i.bottom };
  }
  function Tr(i) {
    var t = i.findDOMNode();
    if (!t) {
      throw new Error('<DraggableCore>: Unmounted during event!');
    }
    return t;
  }
  function Gd() {}
  function Cn(i) {
    if (typeof Symbol == 'function' && typeof Symbol.iterator == 'symbol') {
      Cn = function (e) {
        return typeof e;
      };
    } else {
      Cn = function (e) {
        if (
          e &&
          typeof Symbol == 'function' &&
          e.constructor === Symbol &&
          e !== Symbol.prototype
        ) {
          return 'symbol';
        } else {
          return typeof e;
        }
      };
    }
    return Cn(i);
  }
  function Ar(i) {
    if (i && i.__esModule) {
      return i;
    } else {
      return { default: i };
    }
  }
  function Ea(i) {
    if (typeof WeakMap != 'function') {
      return null;
    }
    var t = new WeakMap();
    var e = new WeakMap();
    return (Ea = function (r) {
      if (r) {
        return e;
      } else {
        return t;
      }
    })(i);
  }
  function Xd(i, t) {
    if (!t && i && i.__esModule) {
      return i;
    }
    if (i === null || (Cn(i) !== 'object' && typeof i != 'function')) {
      return { default: i };
    }
    var e = Ea(t);
    if (e && e.has(i)) {
      return e.get(i);
    }
    var n = {};
    var r = Object.defineProperty && Object.getOwnPropertyDescriptor;
    for (var o in i) {
      if (o !== 'default' && Object.prototype.hasOwnProperty.call(i, o)) {
        var s = r ? Object.getOwnPropertyDescriptor(i, o) : null;
        if (s && (s.get || s.set)) {
          Object.defineProperty(n, o, s);
        } else {
          n[o] = i[o];
        }
      }
    }
    n.default = i;
    if (e) {
      e.set(i, n);
    }
    return n;
  }
  function Yd(i, t) {
    return Qd(i) || Jd(i, t) || Kd(i, t) || Zd();
  }
  function Zd() {
    throw new TypeError(`Invalid attempt to destructure non-iterable instance.
In order to be iterable, non-array objects must have a [Symbol.iterator]() method.`);
  }
  function Kd(i, t) {
    if (i) {
      if (typeof i == 'string') {
        return _a(i, t);
      }
      var e = Object.prototype.toString.call(i).slice(8, -1);
      if (e === 'Object' && i.constructor) {
        e = i.constructor.name;
      }
      if (e === 'Map' || e === 'Set') {
        return Array.from(i);
      }
      if (
        e === 'Arguments' ||
        /^(?:Ui|I)nt(?:8|16|32)(?:Clamped)?Array$/.test(e)
      ) {
        return _a(i, t);
      }
    }
  }
  function _a(i, t) {
    if (t == null || t > i.length) {
      t = i.length;
    }
    var e = 0;
    for (var n = new Array(t); e < t; e++) {
      n[e] = i[e];
    }
    return n;
  }
  function Jd(i, t) {
    var e =
      i == null
        ? null
        : (typeof Symbol != 'undefined' && i[Symbol.iterator]) ||
          i['@@iterator'];
    if (e != null) {
      var n = [];
      var r = true;
      var o = false;
      var s;
      var a;
      try {
        for (
          e = e.call(i);
          !(r = (s = e.next()).done) && (n.push(s.value), !t || n.length !== t);
          r = true
        ) {}
      } catch (l) {
        o = true;
        a = l;
      } finally {
        try {
          if (!r && e.return != null) {
            e.return();
          }
        } finally {
          if (o) {
            throw a;
          }
        }
      }
      return n;
    }
  }
  function Qd(i) {
    if (Array.isArray(i)) {
      return i;
    }
  }
  function $d(i, t) {
    if (!(i instanceof t)) {
      throw new TypeError('Cannot call a class as a function');
    }
  }
  function xa(i, t) {
    for (var e = 0; e < t.length; e++) {
      var n = t[e];
      n.enumerable = n.enumerable || false;
      n.configurable = true;
      if ('value' in n) {
        n.writable = true;
      }
      Object.defineProperty(i, n.key, n);
    }
  }
  function ef(i, t, e) {
    if (t) {
      xa(i.prototype, t);
    }
    if (e) {
      xa(i, e);
    }
    return i;
  }
  function tf(i, t) {
    if (typeof t != 'function' && t !== null) {
      throw new TypeError('Super expression must either be null or a function');
    }
    i.prototype = Object.create(t && t.prototype, {
      constructor: { value: i, writable: true, configurable: true },
    });
    if (t) {
      Or(i, t);
    }
  }
  function Or(i, t) {
    Or =
      Object.setPrototypeOf ||
      function (n, r) {
        n.__proto__ = r;
        return n;
      };
    return Or(i, t);
  }
  function nf(i) {
    var t = of();
    return function () {
      var n = ri(i);
      var r;
      if (t) {
        var o = ri(this).constructor;
        r = Reflect.construct(n, arguments, o);
      } else {
        r = n.apply(this, arguments);
      }
      return rf(this, r);
    };
  }
  function rf(i, t) {
    if (t && (Cn(t) === 'object' || typeof t == 'function')) {
      return t;
    }
    if (t !== void 0) {
      throw new TypeError(
        'Derived constructors may only return object or undefined'
      );
    }
    return Ve(i);
  }
  function Ve(i) {
    if (i === void 0) {
      throw new ReferenceError(
        "this hasn't been initialised - super() hasn't been called"
      );
    }
    return i;
  }
  function of() {
    if (
      typeof Reflect == 'undefined' ||
      !Reflect.construct ||
      Reflect.construct.sham
    ) {
      return false;
    }
    if (typeof Proxy == 'function') {
      return true;
    }
    try {
      Boolean.prototype.valueOf.call(
        Reflect.construct(Boolean, [], function () {})
      );
      return true;
    } catch {
      return false;
    }
  }
  function ri(i) {
    ri = Object.setPrototypeOf
      ? Object.getPrototypeOf
      : function (e) {
          return e.__proto__ || Object.getPrototypeOf(e);
        };
    return ri(i);
  }
  function st(i, t, e) {
    if (t in i) {
      Object.defineProperty(i, t, {
        value: e,
        enumerable: true,
        configurable: true,
        writable: true,
      });
    } else {
      i[t] = e;
    }
    return i;
  }
  function uf(i, t) {
    if (t === 0) {
      return ['just now', 'right now'];
    }
    var e = lf[Math.floor(t / 2)];
    if (i > 1) {
      e += 's';
    }
    return [i + ' ' + e + ' ago', 'in ' + i + ' ' + e];
  }
  function hf(i, t) {
    if (t === 0) {
      return ['\u521A\u521A', '\u7247\u523B\u540E'];
    }
    var e = cf[~~(t / 2)];
    return [i + ' ' + e + '\u524D', i + ' ' + e + '\u540E'];
  }
  function Aa(i) {
    if (i instanceof Date) {
      return i;
    } else if (!isNaN(i) || /^\d+$/.test(i)) {
      return new Date(parseInt(i));
    } else {
      i = (i || '')
        .trim()
        .replace(/\.\d+/, '')
        .replace(/-/, '/')
        .replace(/-/, '/')
        .replace(/(\d)T(\d)/, '$1 $2')
        .replace(/Z/, ' UTC')
        .replace(/([+-]\d\d):?(\d\d)/, ' $1$2');
      return new Date(i);
    }
  }
  function Oa(i, t) {
    var e = i < 0 ? 1 : 0;
    i = Math.abs(i);
    var n = i;
    for (var r = 0; i >= It[r] && r < It.length; r++) {
      i /= It[r];
    }
    i = Math.floor(i);
    r *= 2;
    if (i > (r === 0 ? 9 : 1)) {
      r += 1;
    }
    return t(i, r, n)[e].replace('%s', i.toString());
  }
  function Da(i, t) {
    var e = t ? Aa(t) : new Date();
    return (+e - +Aa(i)) / 1e3;
  }
  function df(i) {
    var t = 1;
    var e = 0;
    for (var n = Math.abs(i); i >= It[e] && e < It.length; e++) {
      i /= It[e];
      t *= It[e];
    }
    n = n % t;
    n = n ? t - n : t;
    return Math.ceil(n);
  }
  function pf(i) {
    return i.getAttribute('datetime');
  }
  function mf(i, t) {
    i.setAttribute(Ra, t);
  }
  function Ma(i) {
    return parseInt(i.getAttribute(Ra));
  }
  function Fa(i, t, e, n) {
    Mr(Ma(i));
    var r = n.relativeDate;
    var o = n.minInterval;
    var s = Da(t, r);
    i.innerText = Oa(s, e);
    var a = setTimeout(function () {
      Fa(i, t, e, n);
    }, Math.min(Math.max(df(s), o || 1) * 1e3, 2147483647));
    Rr[a] = 0;
    mf(i, a);
  }
  function La(i) {
    if (i) {
      Mr(Ma(i));
    } else {
      Object.keys(Rr).forEach(Mr);
    }
  }
  function gf(i, t, e) {
    var n = i.length ? i : [i];
    n.forEach(function (r) {
      Fa(r, pf(r), Pa(t), e || {});
    });
    return n;
  }
  function Sf(i, t) {
    const e = (n) => {
      if (i.current && !i.current.contains(event.target)) {
        t();
      }
    };
    kt(
      () => (
        document.addEventListener('mousedown', e),
        () => document.removeEventListener('mousedown', e)
      )
    );
  }
  function ll(i, t) {
    if (
      i === 1 / 0 ||
      i === -1 / 0 ||
      i !== i ||
      (i && i > -1e3 && i < 1e3) ||
      il.call(/e/, t)
    ) {
      return t;
    }
    var e = /[0-9](?=(?:[0-9]{3})+(?![0-9]))/g;
    if (typeof i == 'number') {
      var n = i < 0 ? -ol(-i) : ol(i);
      if (n !== i) {
        var r = String(n);
        var o = jr.call(t, r.length + 1);
        return (
          Pt.call(r, e, '$&_') +
          '.' +
          Pt.call(Pt.call(o, /([0-9]{3})/g, '$&_'), /_$/, '')
        );
      }
    }
    return Pt.call(t, e, '$&_');
  }
  function ul(i, t, e) {
    var n = (e.quoteStyle || t) === 'double' ? '"' : "'";
    return n + i + n;
  }
  function Ap(i) {
    return Pt.call(String(i), /"/g, '&quot;');
  }
  function Kr(i) {
    return (
      Ot(i) === '[object Array]' && (!Ue || typeof i != 'object' || !(Ue in i))
    );
  }
  function Op(i) {
    return (
      Ot(i) === '[object Date]' && (!Ue || typeof i != 'object' || !(Ue in i))
    );
  }
  function Dp(i) {
    return (
      Ot(i) === '[object RegExp]' && (!Ue || typeof i != 'object' || !(Ue in i))
    );
  }
  function Rp(i) {
    return (
      Ot(i) === '[object Error]' && (!Ue || typeof i != 'object' || !(Ue in i))
    );
  }
  function Mp(i) {
    return (
      Ot(i) === '[object String]' && (!Ue || typeof i != 'object' || !(Ue in i))
    );
  }
  function Fp(i) {
    return (
      Ot(i) === '[object Number]' && (!Ue || typeof i != 'object' || !(Ue in i))
    );
  }
  function Lp(i) {
    return (
      Ot(i) === '[object Boolean]' &&
      (!Ue || typeof i != 'object' || !(Ue in i))
    );
  }
  function cl(i) {
    if (an) {
      return i && typeof i == 'object' && i instanceof Symbol;
    }
    if (typeof i == 'symbol') {
      return true;
    }
    if (!i || typeof i != 'object' || !Xr) {
      return false;
    }
    try {
      Xr.call(i);
      return true;
    } catch {}
    return false;
  }
  function kp(i) {
    if (!i || typeof i != 'object' || !Gr) {
      return false;
    }
    try {
      Gr.call(i);
      return true;
    } catch {}
    return false;
  }
  function At(i, t) {
    return Bp.call(i, t);
  }
  function Ot(i) {
    return Ep.call(i);
  }
  function Np(i) {
    if (i.name) {
      return i.name;
    }
    var t = xp.call(_p.call(i), /^function\s*([\w$]+)/);
    if (t) {
      return t[1];
    } else {
      return null;
    }
  }
  function hl(i, t) {
    if (i.indexOf) {
      return i.indexOf(t);
    }
    var e = 0;
    for (var n = i.length; e < n; e++) {
      if (i[e] === t) {
        return e;
      }
    }
    return -1;
  }
  function Ip(i) {
    if (!fi || !i || typeof i != 'object') {
      return false;
    }
    try {
      fi.call(i);
      try {
        pi.call(i);
      } catch {
        return true;
      }
      return i instanceof Map;
    } catch {}
    return false;
  }
  function Hp(i) {
    if (!On || !i || typeof i != 'object') {
      return false;
    }
    try {
      On.call(i, On);
      try {
        Dn.call(i, Dn);
      } catch {
        return true;
      }
      return i instanceof WeakMap;
    } catch {}
    return false;
  }
  function zp(i) {
    if (!tl || !i || typeof i != 'object') {
      return false;
    }
    try {
      tl.call(i);
      return true;
    } catch {}
    return false;
  }
  function Vp(i) {
    if (!pi || !i || typeof i != 'object') {
      return false;
    }
    try {
      pi.call(i);
      try {
        fi.call(i);
      } catch {
        return true;
      }
      return i instanceof Set;
    } catch {}
    return false;
  }
  function Up(i) {
    if (!Dn || !i || typeof i != 'object') {
      return false;
    }
    try {
      Dn.call(i, Dn);
      try {
        On.call(i, On);
      } catch {
        return true;
      }
      return i instanceof WeakSet;
    } catch {}
    return false;
  }
  function Wp(i) {
    if (!i || typeof i != 'object') {
      return false;
    } else if (typeof HTMLElement != 'undefined' && i instanceof HTMLElement) {
      return true;
    } else {
      return (
        typeof i.nodeName == 'string' && typeof i.getAttribute == 'function'
      );
    }
  }
  function dl(i, t) {
    if (i.length > t.maxStringLength) {
      var e = i.length - t.maxStringLength;
      var n = '... ' + e + ' more character' + (e > 1 ? 's' : '');
      return dl(jr.call(i, 0, t.maxStringLength), t) + n;
    }
    var r = Pt.call(Pt.call(i, /(['\\])/g, '\\$1'), /[\x00-\x1f]/g, jp);
    return ul(r, 'single', t);
  }
  function jp(i) {
    var t = i.charCodeAt(0);
    var e = { 8: 'b', 9: 't', 10: 'n', 12: 'f', 13: 'r' }[t];
    if (e) {
      return '\\' + e;
    } else {
      return '\\x' + (t < 16 ? '0' : '') + Tp.call(t.toString(16));
    }
  }
  function Rn(i) {
    return 'Object(' + i + ')';
  }
  function Jr(i) {
    return i + ' { ? }';
  }
  function fl(i, t, e, n) {
    var r = n ? Qr(e, n) : ft.call(e, ', ');
    return i + ' (' + t + ') {' + r + '}';
  }
  function Gp(i) {
    for (var t = 0; t < i.length; t++) {
      if (
        hl(
          i[t],
          `
`
        ) >= 0
      ) {
        return false;
      }
    }
    return true;
  }
  function qp(i, t) {
    var e;
    if (i.indent === '\x09') {
      e = '\x09';
    } else if (typeof i.indent == 'number' && i.indent > 0) {
      e = ft.call(Array(i.indent + 1), ' ');
    } else {
      return null;
    }
    return { base: e, prev: ft.call(Array(t + 1), e) };
  }
  function Qr(i, t) {
    if (i.length === 0) {
      return '';
    }
    var e =
      `
` +
      t.prev +
      t.base;
    return (
      e +
      ft.call(i, ',' + e) +
      `
` +
      t.prev
    );
  }
  function mi(i, t) {
    var e = Kr(i);
    var n = [];
    if (e) {
      n.length = i.length;
      for (var r = 0; r < i.length; r++) {
        n[r] = At(i, r) ? t(i[r], i) : '';
      }
    }
    var o = typeof qr == 'function' ? qr(i) : [];
    var s;
    if (an) {
      s = {};
      for (var a = 0; a < o.length; a++) {
        s['$' + o[a]] = o[a];
      }
    }
    for (var l in i) {
      if (
        !!At(i, l) &&
        (!e || String(Number(l)) !== l || !(l < i.length)) &&
        (!an || !(s['$' + l] instanceof Symbol))
      ) {
        if (il.call(/[^\w$]/, l)) {
          n.push(t(l, i) + ': ' + t(i[l], i));
        } else {
          n.push(l + ': ' + t(i[l], i));
        }
      }
    }
    if (typeof qr == 'function') {
      for (var u = 0; u < o.length; u++) {
        if (sl.call(i, o[u])) {
          n.push('[' + t(o[u]) + ']: ' + t(i[o[u]], i));
        }
      }
    }
    return n;
  }
  function jv(i) {
    var t = {};
    wi(oo(i), function (e) {
      var n = e[0];
      var r = e[1];
      wi(r, function (o) {
        t[o] = n;
      });
    });
    return t;
  }
  function Gv(i, t) {
    var e = jv(i.pluralTypeToLanguages);
    return e[t] || e[eu.call(t, /-/, 1)[0]] || e.en;
  }
  function qv(i, t, e) {
    return i.pluralTypes[t](e);
  }
  function Xv() {
    var i = {};
    return function (t, e) {
      var n = i[e];
      if (n && !t.pluralTypes[n]) {
        n = null;
        i[e] = n;
      }
      if (!n) {
        n = Gv(t, e);
        if (n) {
          i[e] = n;
        }
      }
      return n;
    };
  }
  function nu(i) {
    return i.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  }
  function Yv(i) {
    var t = (i && i.prefix) || '%{';
    var e = (i && i.suffix) || '}';
    if (t === bi || e === bi) {
      throw new RangeError('"' + bi + '" token is reserved for pluralization');
    }
    return new RegExp(nu(t) + '(.*?)' + nu(e), 'g');
  }
  function ao(i, t, e, n, r) {
    if (typeof i != 'string') {
      throw new TypeError(
        'Polyglot.transformPhrase expects argument #1 to be string'
      );
    }
    if (t == null) {
      return i;
    }
    var o = i;
    var s = n || Kv;
    var a = typeof t == 'number' ? { smart_count: t } : t;
    if (a.smart_count != null && i) {
      var l = r || tu;
      var u = eu.call(i, bi);
      var c = e || 'en';
      var h = Zv(l, c);
      var d = qv(l, h, a.smart_count);
      o = Vv(u[d] || u[0]);
    }
    o = Wv.call(o, s, function (g, y) {
      if (!$l(a, y) || a[y] == null) {
        return g;
      } else {
        return a[y];
      }
    });
    return o;
  }
  function bt(i) {
    var t = i || {};
    this.phrases = {};
    this.extend(t.phrases || {});
    this.currentLocale = t.locale || 'en';
    var e = t.allowMissing ? ao : null;
    this.onMissingKey =
      typeof t.onMissingKey == 'function' ? t.onMissingKey : e;
    this.warn = t.warn || Uv;
    this.tokenRegex = Yv(t.interpolation);
    this.pluralRules = t.pluralRules || tu;
  }
  function gy(i, t) {
    if (t < 3) {
      return lo[i][t - 1];
    } else if (t >= 3 && t <= 10) {
      return lo[i][2];
    } else {
      return lo[i][3];
    }
  }
  function vy(i, t) {
    if (t === 0) {
      return [
        '\u0645\u0646\u0630 \u0644\u062D\u0638\u0627\u062A',
        '\u0628\u0639\u062F \u0644\u062D\u0638\u0627\u062A',
      ];
    }
    var e = gy(Math.floor(t / 2), i);
    return ['\u0645\u0646\u0630 ' + e, '\u0628\u0639\u062F ' + e];
  }
  function wy(i, t) {
    var e = 0;
    var n =
      t == 1 || t == 3 || t == 5 || t == 7 || t == 9 || t == 11 || t == 13;
    if (n && i >= 5) {
      e = 1;
    }
    return [
      [['pr\xE1v\u011B te\u010F', 'pr\xE1v\u011B te\u010F']],
      [
        ['p\u0159ed %s vte\u0159inami', 'za %s vte\u0159iny'],
        ['p\u0159ed %s vte\u0159inami', 'za %s vte\u0159in'],
      ],
      [['p\u0159ed minutou', 'za minutu']],
      [
        ['p\u0159ed %s minutami', 'za %s minuty'],
        ['p\u0159ed %s minutami', 'za %s minut'],
      ],
      [['p\u0159ed hodinou', 'za hodinu']],
      [
        ['p\u0159ed %s hodinami', 'za %s hodiny'],
        ['p\u0159ed %s hodinami', 'za %s hodin'],
      ],
      [['v\u010Dera', 'z\xEDtra']],
      [
        ['p\u0159ed %s dny', 'za %s dny'],
        ['p\u0159ed %s dny', 'za %s dn\u016F'],
      ],
      [['minul\xFD t\xFDden', 'p\u0159\xED\u0161t\xED t\xFDden']],
      [
        ['p\u0159ed %s t\xFDdny', 'za %s t\xFDdny'],
        ['p\u0159ed %s t\xFDdny', 'za %s t\xFDdn\u016F'],
      ],
      [['minul\xFD m\u011Bs\xEDc', 'p\u0159\xEDst\xED m\u011Bs\xEDc']],
      [
        ['p\u0159ed %s m\u011Bs\xEDci', 'za %s m\u011Bs\xEDce'],
        ['p\u0159ed %s m\u011Bs\xEDci', 'za %s m\u011Bs\xEDc\u016F'],
      ],
      [['p\u0159ed rokem', 'p\u0159\xEDst\xED rok']],
      [
        ['p\u0159ed %s lety', 'za %s roky'],
        ['p\u0159ed %s lety', 'za %s let'],
      ],
    ][t][e];
  }
  function Sy(i, t) {
    return [
      ['gerade eben', 'vor einer Weile'],
      ['vor %s Sekunden', 'in %s Sekunden'],
      ['vor 1 Minute', 'in 1 Minute'],
      ['vor %s Minuten', 'in %s Minuten'],
      ['vor 1 Stunde', 'in 1 Stunde'],
      ['vor %s Stunden', 'in %s Stunden'],
      ['vor 1 Tag', 'in 1 Tag'],
      ['vor %s Tagen', 'in %s Tagen'],
      ['vor 1 Woche', 'in 1 Woche'],
      ['vor %s Wochen', 'in %s Wochen'],
      ['vor 1 Monat', 'in 1 Monat'],
      ['vor %s Monaten', 'in %s Monaten'],
      ['vor 1 Jahr', 'in 1 Jahr'],
      ['vor %s Jahren', 'in %s Jahren'],
    ][t];
  }
  function _y(i, t) {
    return [
      [
        '\u03BC\u03CC\u03BB\u03B9\u03C2 \u03C4\u03CE\u03C1\u03B1',
        '\u03C3\u03B5 \u03BB\u03AF\u03B3\u03BF',
      ],
      [
        '%s \u03B4\u03B5\u03C5\u03C4\u03B5\u03C1\u03CC\u03BB\u03B5\u03C0\u03C4\u03B1 \u03C0\u03C1\u03B9\u03BD',
        '\u03C3\u03B5 %s \u03B4\u03B5\u03C5\u03C4\u03B5\u03C1\u03CC\u03BB\u03B5\u03C0\u03C4\u03B1',
      ],
      [
        '1 \u03BB\u03B5\u03C0\u03C4\u03CC \u03C0\u03C1\u03B9\u03BD',
        '\u03C3\u03B5 1 \u03BB\u03B5\u03C0\u03C4\u03CC',
      ],
      [
        '%s \u03BB\u03B5\u03C0\u03C4\u03AC \u03C0\u03C1\u03B9\u03BD',
        '\u03C3\u03B5 %s \u03BB\u03B5\u03C0\u03C4\u03AC',
      ],
      [
        '1 \u03CE\u03C1\u03B1 \u03C0\u03C1\u03B9\u03BD',
        '\u03C3\u03B5 1 \u03CE\u03C1\u03B1',
      ],
      [
        '%s \u03CE\u03C1\u03B5\u03C2 \u03C0\u03C1\u03B9\u03BD',
        '\u03C3\u03B5 %s \u03CE\u03C1\u03B5\u03C2',
      ],
      [
        '1 \u03BC\u03AD\u03C1\u03B1 \u03C0\u03C1\u03B9\u03BD',
        '\u03C3\u03B5 1 \u03BC\u03AD\u03C1\u03B1',
      ],
      [
        '%s \u03BC\u03AD\u03C1\u03B5\u03C2 \u03C0\u03C1\u03B9\u03BD',
        '\u03C3\u03B5 %s \u03BC\u03AD\u03C1\u03B5\u03C2',
      ],
      [
        '1 \u03B5\u03B2\u03B4\u03BF\u03BC\u03AC\u03B4\u03B1 \u03C0\u03C1\u03B9\u03BD',
        '\u03C3\u03B5 1 \u03B5\u03B2\u03B4\u03BF\u03BC\u03AC\u03B4\u03B1',
      ],
      [
        '%s \u03B5\u03B2\u03B4\u03BF\u03BC\u03AC\u03B4\u03B5\u03C2 \u03C0\u03C1\u03B9\u03BD',
        '\u03C3\u03B5 %s \u03B5\u03B2\u03B4\u03BF\u03BC\u03AC\u03B4\u03B5\u03C2',
      ],
      [
        '1 \u03BC\u03AE\u03BD\u03B1 \u03C0\u03C1\u03B9\u03BD',
        '\u03C3\u03B5 1 \u03BC\u03AE\u03BD\u03B1',
      ],
      [
        '%s \u03BC\u03AE\u03BD\u03B5\u03C2 \u03C0\u03C1\u03B9\u03BD',
        '\u03C3\u03B5 %s \u03BC\u03AE\u03BD\u03B5\u03C2',
      ],
      [
        '1 \u03C7\u03C1\u03CC\u03BD\u03BF \u03C0\u03C1\u03B9\u03BD',
        '\u03C3\u03B5 1 \u03C7\u03C1\u03CC\u03BD\u03BF',
      ],
      [
        '%s \u03C7\u03C1\u03CC\u03BD\u03B9\u03B1 \u03C0\u03C1\u03B9\u03BD',
        '\u03C3\u03B5 %s \u03C7\u03C1\u03CC\u03BD\u03B9\u03B1',
      ],
    ][t];
  }
  function Ty(i, t) {
    return [
      ['justo ahora', 'en un rato'],
      ['hace %s segundos', 'en %s segundos'],
      ['hace 1 minuto', 'en 1 minuto'],
      ['hace %s minutos', 'en %s minutos'],
      ['hace 1 hora', 'en 1 hora'],
      ['hace %s horas', 'en %s horas'],
      ['hace 1 d\xEDa', 'en 1 d\xEDa'],
      ['hace %s d\xEDas', 'en %s d\xEDas'],
      ['hace 1 semana', 'en 1 semana'],
      ['hace %s semanas', 'en %s semanas'],
      ['hace 1 mes', 'en 1 mes'],
      ['hace %s meses', 'en %s meses'],
      ['hace 1 a\xF1o', 'en 1 a\xF1o'],
      ['hace %s a\xF1os', 'en %s a\xF1os'],
    ][t];
  }
  function Py(i, t) {
    return [
      ['juuri \xE4sken', 'juuri nyt'],
      ['%s sekuntia sitten', '%s sekunnin p\xE4\xE4st\xE4'],
      ['minuutti sitten', 'minuutin p\xE4\xE4st\xE4'],
      ['%s minuuttia sitten', '%s minuutin p\xE4\xE4st\xE4'],
      ['tunti sitten', 'tunnin p\xE4\xE4st\xE4'],
      ['%s tuntia sitten', '%s tunnin p\xE4\xE4st\xE4'],
      ['p\xE4iv\xE4 sitten', 'p\xE4iv\xE4n p\xE4\xE4st\xE4'],
      ['%s p\xE4iv\xE4\xE4 sitten', '%s p\xE4iv\xE4n p\xE4\xE4st\xE4'],
      ['viikko sitten', 'viikon p\xE4\xE4st\xE4'],
      ['%s viikkoa sitten', '%s viikon p\xE4\xE4st\xE4'],
      ['kuukausi sitten', 'kuukauden p\xE4\xE4st\xE4'],
      ['%s kuukautta sitten', '%s kuukauden p\xE4\xE4st\xE4'],
      ['vuosi sitten', 'vuoden p\xE4\xE4st\xE4'],
      ['%s vuotta sitten', '%s vuoden p\xE4\xE4st\xE4'],
    ][t];
  }
  function Oy(i, t) {
    return [
      ["\xE0 l'instant", 'dans un instant'],
      ['il y a %s secondes', 'dans %s secondes'],
      ['il y a 1 minute', 'dans 1 minute'],
      ['il y a %s minutes', 'dans %s minutes'],
      ['il y a 1 heure', 'dans 1 heure'],
      ['il y a %s heures', 'dans %s heures'],
      ['il y a 1 jour', 'dans 1 jour'],
      ['il y a %s jours', 'dans %s jours'],
      ['il y a 1 semaine', 'dans 1 semaine'],
      ['il y a %s semaines', 'dans %s semaines'],
      ['il y a 1 mois', 'dans 1 mois'],
      ['il y a %s mois', 'dans %s mois'],
      ['il y a 1 an', 'dans 1 an'],
      ['il y a %s ans', 'dans %s ans'],
    ][t];
  }
  function Ry(i, t) {
    return [
      ['xusto agora', 'daqu\xED a un pouco'],
      ['hai %s segundos', 'en %s segundos'],
      ['hai 1 minuto', 'nun minuto'],
      ['hai %s minutos', 'en %s minutos'],
      ['hai 1 hora', 'nunha hora'],
      ['hai %s horas', 'en %s horas'],
      ['hai 1 d\xEDa', 'nun d\xEDa'],
      ['hai %s d\xEDas', 'en %s d\xEDas'],
      ['hai 1 semana', 'nunha semana'],
      ['hai %s semanas', 'en %s semanas'],
      ['hai 1 mes', 'nun mes'],
      ['hai %s meses', 'en %s meses'],
      ['hai 1 ano', 'nun ano'],
      ['hai %s anos', 'en %s anos'],
    ][t];
  }
  function Fy(i, t) {
    return [
      ['\u0905\u092D\u0940', '\u0915\u0941\u091B \u0938\u092E\u092F'],
      [
        '%s \u0938\u0947\u0915\u0902\u0921 \u092A\u0939\u0932\u0947',
        '%s \u0938\u0947\u0915\u0902\u0921 \u092E\u0947\u0902',
      ],
      [
        '1 \u092E\u093F\u0928\u091F \u092A\u0939\u0932\u0947',
        '1 \u092E\u093F\u0928\u091F \u092E\u0947\u0902',
      ],
      [
        '%s \u092E\u093F\u0928\u091F \u092A\u0939\u0932\u0947',
        '%s \u092E\u093F\u0928\u091F \u092E\u0947\u0902',
      ],
      [
        '1 \u0918\u0902\u091F\u0947 \u092A\u0939\u0932\u0947',
        '1 \u0918\u0902\u091F\u0947 \u092E\u0947\u0902',
      ],
      [
        '%s \u0918\u0902\u091F\u0947 \u092A\u0939\u0932\u0947',
        '%s \u0918\u0902\u091F\u0947 \u092E\u0947\u0902',
      ],
      [
        '1 \u0926\u093F\u0928 \u092A\u0939\u0932\u0947',
        '1 \u0926\u093F\u0928 \u092E\u0947\u0902',
      ],
      [
        '%s \u0926\u093F\u0928 \u092A\u0939\u0932\u0947',
        '%s \u0926\u093F\u0928\u094B\u0902 \u092E\u0947\u0902',
      ],
      [
        '1 \u0938\u092A\u094D\u0924\u093E\u0939 \u092A\u0939\u0932\u0947',
        '1 \u0938\u092A\u094D\u0924\u093E\u0939 \u092E\u0947\u0902',
      ],
      [
        '%s \u0939\u092B\u094D\u0924\u0947 \u092A\u0939\u0932\u0947',
        '%s \u0939\u092B\u094D\u0924\u094B\u0902 \u092E\u0947\u0902',
      ],
      [
        '1 \u092E\u0939\u0940\u0928\u0947 \u092A\u0939\u0932\u0947',
        '1 \u092E\u0939\u0940\u0928\u0947 \u092E\u0947\u0902',
      ],
      [
        '%s \u092E\u0939\u0940\u0928\u0947 \u092A\u0939\u0932\u0947',
        '%s \u092E\u0939\u0940\u0928\u094B\u0902 \u092E\u0947\u0902',
      ],
      [
        '1 \u0938\u093E\u0932 \u092A\u0939\u0932\u0947',
        '1 \u0938\u093E\u0932 \u092E\u0947\u0902',
      ],
      [
        '%s \u0938\u093E\u0932 \u092A\u0939\u0932\u0947',
        '%s \u0938\u093E\u0932 \u092E\u0947\u0902',
      ],
    ][t];
  }
  function ky(i, t) {
    return [
      ['poco fa', 'fra poco'],
      ['%s secondi fa', 'fra %s secondi'],
      ['un minuto fa', 'fra un minuto'],
      ['%s minuti fa', 'fra %s minuti'],
      ["un'ora fa", "fra un'ora"],
      ['%s ore fa', 'fra %s ore'],
      ['un giorno fa', 'fra un giorno'],
      ['%s giorni fa', 'fra %s giorni'],
      ['una settimana fa', 'fra una settimana'],
      ['%s settimane fa', 'fra %s settimane'],
      ['un mese fa', 'fra un mese'],
      ['%s mesi fa', 'fra %s mesi'],
      ['un anno fa', 'fra un anno'],
      ['%s anni fa', 'fra %s anni'],
    ][t];
  }
  function Ny(i, t) {
    return [
      ['\uBC29\uAE08', '\uACE7'],
      ['%s\uCD08 \uC804', '%s\uCD08 \uD6C4'],
      ['1\uBD84 \uC804', '1\uBD84 \uD6C4'],
      ['%s\uBD84 \uC804', '%s\uBD84 \uD6C4'],
      ['1\uC2DC\uAC04 \uC804', '1\uC2DC\uAC04 \uD6C4'],
      ['%s\uC2DC\uAC04 \uC804', '%s\uC2DC\uAC04 \uD6C4'],
      ['1\uC77C \uC804', '1\uC77C \uD6C4'],
      ['%s\uC77C \uC804', '%s\uC77C \uD6C4'],
      ['1\uC8FC\uC77C \uC804', '1\uC8FC\uC77C \uD6C4'],
      ['%s\uC8FC\uC77C \uC804', '%s\uC8FC\uC77C \uD6C4'],
      ['1\uAC1C\uC6D4 \uC804', '1\uAC1C\uC6D4 \uD6C4'],
      ['%s\uAC1C\uC6D4 \uC804', '%s\uAC1C\uC6D4 \uD6C4'],
      ['1\uB144 \uC804', '1\uB144 \uD6C4'],
      ['%s\uB144 \uC804', '%s\uB144 \uD6C4'],
    ][t];
  }
  function Hy(i, t) {
    return [
      ['recent', 'binnenkort'],
      ['%s seconden geleden', 'binnen %s seconden'],
      ['1 minuut geleden', 'binnen 1 minuut'],
      ['%s minuten geleden', 'binnen %s minuten'],
      ['1 uur geleden', 'binnen 1 uur'],
      ['%s uur geleden', 'binnen %s uur'],
      ['1 dag geleden', 'binnen 1 dag'],
      ['%s dagen geleden', 'binnen %s dagen'],
      ['1 week geleden', 'binnen 1 week'],
      ['%s weken geleden', 'binnen %s weken'],
      ['1 maand geleden', 'binnen 1 maand'],
      ['%s maanden geleden', 'binnen %s maanden'],
      ['1 jaar geleden', 'binnen 1 jaar'],
      ['%s jaar geleden', 'binnen %s jaar'],
    ][t];
  }
  function Vy(i, t) {
    return [
      ['agora mesmo', 'agora'],
      ['h\xE1 %s segundos', 'em %s segundos'],
      ['h\xE1 um minuto', 'em um minuto'],
      ['h\xE1 %s minutos', 'em %s minutos'],
      ['h\xE1 uma hora', 'em uma hora'],
      ['h\xE1 %s horas', 'em %s horas'],
      ['h\xE1 um dia', 'em um dia'],
      ['h\xE1 %s dias', 'em %s dias'],
      ['h\xE1 uma semana', 'em uma semana'],
      ['h\xE1 %s semanas', 'em %s semanas'],
      ['h\xE1 um m\xEAs', 'em um m\xEAs'],
      ['h\xE1 %s meses', 'em %s meses'],
      ['h\xE1 um ano', 'em um ano'],
      ['h\xE1 %s anos', 'em %s anos'],
    ][t];
  }
  function Vt(i, t, e, n, r) {
    var o = r % 10;
    var s = n;
    if (r === 1) {
      s = i;
    } else if (o === 1 && r > 20) {
      s = t;
    } else if (o > 1 && o < 5 && (r > 20 || r < 10)) {
      s = e;
    }
    return s;
  }
  function Wy(i, t) {
    switch (t) {
      case 0:
        return [
          '\u0442\u043E\u043B\u044C\u043A\u043E \u0447\u0442\u043E',
          '\u0447\u0435\u0440\u0435\u0437 \u043D\u0435\u0441\u043A\u043E\u043B\u044C\u043A\u043E \u0441\u0435\u043A\u0443\u043D\u0434',
        ];
      case 1:
        return [
          vu(i) + ' \u043D\u0430\u0437\u0430\u0434',
          '\u0447\u0435\u0440\u0435\u0437 ' + vu(i),
        ];
      case 2:
      case 3:
        return [
          yu(i) + ' \u043D\u0430\u0437\u0430\u0434',
          '\u0447\u0435\u0440\u0435\u0437 ' + yu(i),
        ];
      case 4:
      case 5:
        return [
          wu(i) + ' \u043D\u0430\u0437\u0430\u0434',
          '\u0447\u0435\u0440\u0435\u0437 ' + wu(i),
        ];
      case 6:
        return [
          '\u0432\u0447\u0435\u0440\u0430',
          '\u0437\u0430\u0432\u0442\u0440\u0430',
        ];
      case 7:
        return [
          bu(i) + ' \u043D\u0430\u0437\u0430\u0434',
          '\u0447\u0435\u0440\u0435\u0437 ' + bu(i),
        ];
      case 8:
      case 9:
        return [
          Su(i) + ' \u043D\u0430\u0437\u0430\u0434',
          '\u0447\u0435\u0440\u0435\u0437 ' + Su(i),
        ];
      case 10:
      case 11:
        return [
          Eu(i) + ' \u043D\u0430\u0437\u0430\u0434',
          '\u0447\u0435\u0440\u0435\u0437 ' + Eu(i),
        ];
      case 12:
      case 13:
        return [
          _u(i) + ' \u043D\u0430\u0437\u0430\u0434',
          '\u0447\u0435\u0440\u0435\u0437 ' + _u(i),
        ];
      default:
        return ['', ''];
    }
  }
  function Gy(i, t) {
    return [
      ['just nu', 'om en stund'],
      ['%s sekunder sedan', 'om %s sekunder'],
      ['1 minut sedan', 'om 1 minut'],
      ['%s minuter sedan', 'om %s minuter'],
      ['1 timme sedan', 'om 1 timme'],
      ['%s timmar sedan', 'om %s timmar'],
      ['1 dag sedan', 'om 1 dag'],
      ['%s dagar sedan', 'om %s dagar'],
      ['1 vecka sedan', 'om 1 vecka'],
      ['%s veckor sedan', 'om %s veckor'],
      ['1 m\xE5nad sedan', 'om 1 m\xE5nad'],
      ['%s m\xE5nader sedan', 'om %s m\xE5nader'],
      ['1 \xE5r sedan', 'om 1 \xE5r'],
      ['%s \xE5r sedan', 'om %s \xE5r'],
    ][t];
  }
  function Xy(i, t) {
    return [
      [
        '\u0E40\u0E21\u0E37\u0E48\u0E2D\u0E2A\u0E31\u0E01\u0E04\u0E23\u0E39\u0E48\u0E19\u0E35\u0E49',
        '\u0E2D\u0E35\u0E01\u0E2A\u0E31\u0E01\u0E04\u0E23\u0E39\u0E48',
      ],
      [
        '%s \u0E27\u0E34\u0E19\u0E32\u0E17\u0E35\u0E17\u0E35\u0E48\u0E41\u0E25\u0E49\u0E27',
        '\u0E43\u0E19 %s \u0E27\u0E34\u0E19\u0E32\u0E17\u0E35',
      ],
      [
        '1 \u0E19\u0E32\u0E17\u0E35\u0E17\u0E35\u0E48\u0E41\u0E25\u0E49\u0E27',
        '\u0E43\u0E19 1 \u0E19\u0E32\u0E17\u0E35',
      ],
      [
        '%s \u0E19\u0E32\u0E17\u0E35\u0E17\u0E35\u0E48\u0E41\u0E25\u0E49\u0E27',
        '\u0E43\u0E19 %s \u0E19\u0E32\u0E17\u0E35',
      ],
      [
        '1 \u0E0A\u0E31\u0E48\u0E27\u0E42\u0E21\u0E07\u0E17\u0E35\u0E48\u0E41\u0E25\u0E49\u0E27',
        '\u0E43\u0E19 1 \u0E0A\u0E31\u0E48\u0E27\u0E42\u0E21\u0E07',
      ],
      [
        '%s \u0E0A\u0E31\u0E48\u0E27\u0E42\u0E21\u0E07\u0E17\u0E35\u0E48\u0E41\u0E25\u0E49\u0E27',
        '\u0E43\u0E19 %s \u0E0A\u0E31\u0E48\u0E27\u0E42\u0E21\u0E07',
      ],
      [
        '1 \u0E27\u0E31\u0E19\u0E17\u0E35\u0E48\u0E41\u0E25\u0E49\u0E27',
        '\u0E43\u0E19 1 \u0E27\u0E31\u0E19',
      ],
      [
        '%s \u0E27\u0E31\u0E19\u0E17\u0E35\u0E48\u0E41\u0E25\u0E49\u0E27',
        '\u0E43\u0E19 %s \u0E27\u0E31\u0E19',
      ],
      [
        '1 \u0E2D\u0E32\u0E17\u0E34\u0E15\u0E22\u0E4C\u0E17\u0E35\u0E48\u0E41\u0E25\u0E49\u0E27',
        '\u0E43\u0E19 1 \u0E2D\u0E32\u0E17\u0E34\u0E15\u0E22\u0E4C',
      ],
      [
        '%s \u0E2D\u0E32\u0E17\u0E34\u0E15\u0E22\u0E4C\u0E17\u0E35\u0E48\u0E41\u0E25\u0E49\u0E27',
        '\u0E43\u0E19 %s \u0E2D\u0E32\u0E17\u0E34\u0E15\u0E22\u0E4C',
      ],
      [
        '1 \u0E40\u0E14\u0E37\u0E2D\u0E19\u0E17\u0E35\u0E48\u0E41\u0E25\u0E49\u0E27',
        '\u0E43\u0E19 1 \u0E40\u0E14\u0E37\u0E2D\u0E19',
      ],
      [
        '%s \u0E40\u0E14\u0E37\u0E2D\u0E19\u0E17\u0E35\u0E48\u0E41\u0E25\u0E49\u0E27',
        '\u0E43\u0E19 %s \u0E40\u0E14\u0E37\u0E2D\u0E19',
      ],
      [
        '1 \u0E1B\u0E35\u0E17\u0E35\u0E48\u0E41\u0E25\u0E49\u0E27',
        '\u0E43\u0E19 1 \u0E1B\u0E35',
      ],
      [
        '%s \u0E1B\u0E35\u0E17\u0E35\u0E48\u0E41\u0E25\u0E49\u0E27',
        '\u0E43\u0E19 %s \u0E1B\u0E35',
      ],
    ][t];
  }
  function Zy(i, t) {
    return [
      ['az \xF6nce', '\u015Fimdi'],
      ['%s saniye \xF6nce', '%s saniye i\xE7inde'],
      ['1 dakika \xF6nce', '1 dakika i\xE7inde'],
      ['%s dakika \xF6nce', '%s dakika i\xE7inde'],
      ['1 saat \xF6nce', '1 saat i\xE7inde'],
      ['%s saat \xF6nce', '%s saat i\xE7inde'],
      ['1 g\xFCn \xF6nce', '1 g\xFCn i\xE7inde'],
      ['%s g\xFCn \xF6nce', '%s g\xFCn i\xE7inde'],
      ['1 hafta \xF6nce', '1 hafta i\xE7inde'],
      ['%s hafta \xF6nce', '%s hafta i\xE7inde'],
      ['1 ay \xF6nce', '1 ay i\xE7inde'],
      ['%s ay \xF6nce', '%s ay i\xE7inde'],
      ['1 y\u0131l \xF6nce', '1 y\u0131l i\xE7inde'],
      ['%s y\u0131l \xF6nce', '%s y\u0131l i\xE7inde'],
    ][t];
  }
  function e0(i) {
    var t = ho(i, 'line-height');
    var e = parseFloat(t, 10);
    if (t === e + '') {
      var n = i.style.lineHeight;
      i.style.lineHeight = t + 'em';
      t = ho(i, 'line-height');
      e = parseFloat(t, 10);
      if (n) {
        i.style.lineHeight = n;
      } else {
        delete i.style.lineHeight;
      }
    }
    if (t.indexOf('pt') === -1) {
      if (t.indexOf('mm') === -1) {
        if (t.indexOf('cm') === -1) {
          if (t.indexOf('in') === -1) {
            if (t.indexOf('pc') !== -1) {
              e *= 16;
            }
          } else {
            e *= 96;
          }
        } else {
          e *= 96;
          e /= 2.54;
        }
      } else {
        e *= 96;
        e /= 25.4;
      }
    } else {
      e *= 4;
      e /= 3;
    }
    e = Math.round(e);
    if (t === 'normal') {
      var r = i.nodeName;
      var o = document.createElement(r);
      o.innerHTML = '&nbsp;';
      if (r.toUpperCase() === 'TEXTAREA') {
        o.setAttribute('rows', '1');
      }
      var s = ho(i, 'font-size');
      o.style.fontSize = s;
      o.style.padding = '0px';
      o.style.border = '0px';
      var a = document.body;
      a.appendChild(o);
      var l = o.offsetHeight;
      e = l;
      a.removeChild(o);
    }
    return e;
  }
  function ne() {
    ne =
      Object.assign ||
      function (i) {
        for (var t = 1; t < arguments.length; t++) {
          var e = arguments[t];
          for (var n in e) {
            if (Object.prototype.hasOwnProperty.call(e, n)) {
              i[n] = e[n];
            }
          }
        }
        return i;
      };
    return ne.apply(this, arguments);
  }
  function l0(i) {
    if (i.sheet) {
      return i.sheet;
    }
    for (var t = 0; t < document.styleSheets.length; t++) {
      if (document.styleSheets[t].ownerNode === i) {
        return document.styleSheets[t];
      }
    }
  }
  function u0(i) {
    var t = document.createElement('style');
    t.setAttribute('data-emotion', i.key);
    if (i.nonce !== void 0) {
      t.setAttribute('nonce', i.nonce);
    }
    t.appendChild(document.createTextNode(''));
    t.setAttribute('data-s', '');
    return t;
  }
  function p0(i, t) {
    return (
      (((((((t << 2) ^ qe(i, 0)) << 2) ^ qe(i, 1)) << 2) ^ qe(i, 2)) << 2) ^
      qe(i, 3)
    );
  }
  function Mu(i) {
    return i.trim();
  }
  function m0(i, t) {
    if ((i = t.exec(i))) {
      return i[0];
    } else {
      return i;
    }
  }
  function be(i, t, e) {
    return i.replace(t, e);
  }
  function go(i, t) {
    return i.indexOf(t);
  }
  function qe(i, t) {
    return i.charCodeAt(t) | 0;
  }
  function Ln(i, t, e) {
    return i.slice(t, e);
  }
  function pt(i) {
    return i.length;
  }
  function vo(i) {
    return i.length;
  }
  function Ti(i, t) {
    t.push(i);
    return i;
  }
  function g0(i, t) {
    return i.map(t).join('');
  }
  function Pi(i, t, e, n, r, o, s) {
    return {
      value: i,
      root: t,
      parent: e,
      type: n,
      props: r,
      children: o,
      line: Ci,
      column: un,
      length: s,
      return: '',
    };
  }
  function kn(i, t) {
    return f0(
      Pi('', null, null, '', null, null, 0),
      i,
      { length: -i.length },
      t
    );
  }
  function v0() {
    return De;
  }
  function y0() {
    De = Xe > 0 ? qe(cn, --Xe) : 0;
    un--;
    if (De === 10) {
      un = 1;
      Ci--;
    }
    return De;
  }
  function $e() {
    De = Xe < Fu ? qe(cn, Xe++) : 0;
    un++;
    if (De === 10) {
      un = 1;
      Ci++;
    }
    return De;
  }
  function mt() {
    return qe(cn, Xe);
  }
  function Ai() {
    return Xe;
  }
  function Bn(i, t) {
    return Ln(cn, i, t);
  }
  function Nn(i) {
    switch (i) {
      case 0:
      case 9:
      case 10:
      case 13:
      case 32:
        return 5;
      case 33:
      case 43:
      case 44:
      case 47:
      case 62:
      case 64:
      case 126:
      case 59:
      case 123:
      case 125:
        return 4;
      case 58:
        return 3;
      case 34:
      case 39:
      case 40:
      case 91:
        return 2;
      case 41:
      case 93:
        return 1;
    }
    return 0;
  }
  function Lu(i) {
    Ci = un = 1;
    Fu = pt((cn = i));
    Xe = 0;
    return [];
  }
  function ku(i) {
    cn = '';
    return i;
  }
  function Oi(i) {
    return Mu(Bn(Xe - 1, yo(i === 91 ? i + 2 : i === 40 ? i + 1 : i)));
  }
  function w0(i) {
    while ((De = mt()) && De < 33) {
      $e();
    }
    if (Nn(i) > 2 || Nn(De) > 3) {
      return '';
    } else {
      return ' ';
    }
  }
  function b0(i, t) {
    while (
      --t &&
      $e() &&
      !(De < 48) &&
      !(De > 102) &&
      (!(De > 57) || !(De < 65)) &&
      (!(De > 70) || !(De < 97))
    ) {}
    return Bn(i, Ai() + (t < 6 && mt() == 32 && $e() == 32));
  }
  function yo(i) {
    while ($e()) {
      switch (De) {
        case i:
          return Xe;
        case 34:
        case 39:
          if (i !== 34 && i !== 39) {
            yo(De);
          }
          break;
        case 40:
          if (i === 41) {
            yo(i);
          }
          break;
        case 92:
          $e();
          break;
      }
    }
    return Xe;
  }
  function S0(i, t) {
    while ($e() && i + De !== 47 + 10) {
      if (i + De === 42 + 42 && mt() === 47) {
        break;
      }
    }
    return '/*' + Bn(t, Xe - 1) + '*' + xi(i === 47 ? i : $e());
  }
  function E0(i) {
    while (!Nn(mt())) {
      $e();
    }
    return Bn(i, Xe);
  }
  function _0(i) {
    return ku(Di('', null, null, null, [''], (i = Lu(i)), 0, [0], i));
  }
  function Di(i, t, e, n, r, o, s, a, l) {
    var u = 0;
    var c = 0;
    var h = s;
    var d = 0;
    var g = 0;
    var y = 0;
    var x = 1;
    var b = 1;
    var T = 1;
    var f = 0;
    var E = '';
    var A = r;
    var C = o;
    var O = n;
    for (var D = E; b; ) {
      switch (((y = f), (f = $e()))) {
        case 40:
          if (y != 108 && D.charCodeAt(h - 1) == 58) {
            if (go((D += be(Oi(f), '&', '&\x0C')), '&\x0C') != -1) {
              T = -1;
            }
            break;
          }
        case 34:
        case 39:
        case 91:
          D += Oi(f);
          break;
        case 9:
        case 10:
        case 13:
        case 32:
          D += w0(y);
          break;
        case 92:
          D += b0(Ai() - 1, 7);
          continue;
        case 47:
          switch (mt()) {
            case 42:
            case 47:
              Ti(x0(S0($e(), Ai()), t, e), l);
              break;
            default:
              D += '/';
          }
          break;
        case 123 * x:
          a[u++] = pt(D) * T;
        case 125 * x:
        case 59:
        case 0:
          switch (f) {
            case 0:
            case 125:
              b = 0;
            case 59 + c:
              if (g > 0 && pt(D) - h) {
                Ti(
                  g > 32
                    ? Nu(D + ';', n, e, h - 1)
                    : Nu(be(D, ' ', '') + ';', n, e, h - 2),
                  l
                );
              }
              break;
            case 59:
              D += ';';
            default:
              Ti((O = Bu(D, t, e, u, c, r, a, E, (A = []), (C = []), h)), o);
              if (f === 123) {
                if (c === 0) {
                  Di(D, t, O, O, A, o, h, a, C);
                } else {
                  switch (d) {
                    case 100:
                    case 109:
                    case 115:
                      Di(
                        i,
                        O,
                        O,
                        n && Ti(Bu(i, O, O, 0, 0, r, a, E, r, (A = []), h), C),
                        r,
                        C,
                        h,
                        a,
                        n ? A : C
                      );
                      break;
                    default:
                      Di(D, O, O, O, [''], C, 0, a, C);
                  }
                }
              }
          }
          u = c = g = 0;
          x = T = 1;
          E = D = '';
          h = s;
          break;
        case 58:
          h = 1 + pt(D);
          g = y;
        default:
          if (x < 1) {
            if (f == 123) {
              --x;
            } else if (f == 125 && x++ == 0 && y0() == 125) {
              continue;
            }
          }
          switch (((D += xi(f)), f * x)) {
            case 38:
              T = c > 0 ? 1 : ((D += '\x0C'), -1);
              break;
            case 44:
              a[u++] = (pt(D) - 1) * T;
              T = 1;
              break;
            case 64:
              if (mt() === 45) {
                D += Oi($e());
              }
              d = mt();
              c = h = pt((E = D += E0(Ai())));
              f++;
              break;
            case 45:
              if (y === 45 && pt(D) == 2) {
                x = 0;
              }
          }
      }
    }
    return o;
  }
  function Bu(i, t, e, n, r, o, s, a, l, u, c) {
    var h = r - 1;
    var d = r === 0 ? o : [''];
    var g = vo(d);
    var y = 0;
    var x = 0;
    for (var b = 0; y < n; ++y) {
      var T = 0;
      var f = Ln(i, h + 1, (h = d0((x = s[y]))));
      for (var E = i; T < g; ++T) {
        if ((E = Mu(x > 0 ? d[T] + ' ' + f : be(f, /&\f/g, d[T])))) {
          l[b++] = E;
        }
      }
    }
    return Pi(i, t, e, r === 0 ? po : a, l, u, c);
  }
  function x0(i, t, e) {
    return Pi(i, t, e, Du, xi(v0()), Ln(i, 2, -2), 0);
  }
  function Nu(i, t, e, n) {
    return Pi(i, t, e, mo, Ln(i, 0, n), Ln(i, n + 1, -1), n);
  }
  function Iu(i, t) {
    switch (p0(i, t)) {
      case 5103:
        return we + 'print-' + i + i;
      case 5737:
      case 4201:
      case 3177:
      case 3433:
      case 1641:
      case 4457:
      case 2921:
      case 5572:
      case 6356:
      case 5844:
      case 3191:
      case 6645:
      case 3005:
      case 6391:
      case 5879:
      case 5623:
      case 6135:
      case 4599:
      case 4855:
      case 4215:
      case 6389:
      case 5109:
      case 5365:
      case 5621:
      case 3829:
        return we + i + i;
      case 5349:
      case 4246:
      case 4810:
      case 6968:
      case 2756:
        return we + i + _i + i + We + i + i;
      case 6828:
      case 4268:
        return we + i + We + i + i;
      case 6165:
        return we + i + We + 'flex-' + i + i;
      case 5187:
        return (
          we +
          i +
          be(i, /(\w+).+(:[^]+)/, we + 'box-$1$2' + We + 'flex-$1$2') +
          i
        );
      case 5443:
        return we + i + We + 'flex-item-' + be(i, /flex-|-self/, '') + i;
      case 4675:
        return (
          we +
          i +
          We +
          'flex-line-pack' +
          be(i, /align-content|flex-|-self/, '') +
          i
        );
      case 5548:
        return we + i + We + be(i, 'shrink', 'negative') + i;
      case 5292:
        return we + i + We + be(i, 'basis', 'preferred-size') + i;
      case 6060:
        return (
          we +
          'box-' +
          be(i, '-grow', '') +
          we +
          i +
          We +
          be(i, 'grow', 'positive') +
          i
        );
      case 4554:
        return we + be(i, /([^-])(transform)/g, '$1' + we + '$2') + i;
      case 6187:
        return (
          be(
            be(be(i, /(zoom-|grab)/, we + '$1'), /(image-set)/, we + '$1'),
            i,
            ''
          ) + i
        );
      case 5495:
      case 3959:
        return be(i, /(image-set\([^]*)/, we + '$1$`$1');
      case 4968:
        return (
          be(
            be(
              i,
              /(.+:)(flex-)?(.*)/,
              we + 'box-pack:$3' + We + 'flex-pack:$3'
            ),
            /s.+-b[^;]+/,
            'justify'
          ) +
          we +
          i +
          i
        );
      case 4095:
      case 3583:
      case 4068:
      case 2532:
        return be(i, /(.+)-inline(.+)/, we + '$1$2') + i;
      case 8116:
      case 7059:
      case 5753:
      case 5535:
      case 5445:
      case 5701:
      case 4933:
      case 4677:
      case 5533:
      case 5789:
      case 5021:
      case 4765:
        if (pt(i) - 1 - t > 6) {
          switch (qe(i, t + 1)) {
            case 109:
              if (qe(i, t + 4) !== 45) {
                break;
              }
            case 102:
              return (
                be(
                  i,
                  /(.+:)(.+)-([^]+)/,
                  '$1' +
                    we +
                    '$2-$3$1' +
                    _i +
                    (qe(i, t + 3) == 108 ? '$3' : '$2-$3')
                ) + i
              );
            case 115:
              if (~go(i, 'stretch')) {
                return Iu(be(i, 'stretch', 'fill-available'), t) + i;
              } else {
                return i;
              }
          }
        }
        break;
      case 4949:
        if (qe(i, t + 1) !== 115) {
          break;
        }
      case 6444:
        switch (qe(i, pt(i) - 3 - (~go(i, '!important') && 10))) {
          case 107:
            return be(i, ':', ':' + we) + i;
          case 101:
            return (
              be(
                i,
                /(.+:)([^;!]+)(;|!.+)?/,
                '$1' +
                  we +
                  (qe(i, 14) === 45 ? 'inline-' : '') +
                  'box$3$1' +
                  we +
                  '$2$3$1' +
                  We +
                  '$2box$3'
              ) + i
            );
        }
        break;
      case 5936:
        switch (qe(i, t + 11)) {
          case 114:
            return we + i + We + be(i, /[svh]\w+-[tblr]{2}/, 'tb') + i;
          case 108:
            return we + i + We + be(i, /[svh]\w+-[tblr]{2}/, 'tb-rl') + i;
          case 45:
            return we + i + We + be(i, /[svh]\w+-[tblr]{2}/, 'lr') + i;
        }
        return we + i + We + i + i;
    }
    return i;
  }
  function hn(i, t) {
    var e = '';
    var n = vo(i);
    for (var r = 0; r < n; r++) {
      e += t(i[r], r, i, t) || '';
    }
    return e;
  }
  function T0(i, t, e, n) {
    switch (i.type) {
      case h0:
      case mo:
        return (i.return = i.return || i.value);
      case Du:
        return '';
      case Ru:
        return (i.return = i.value + '{' + hn(i.children, n) + '}');
      case po:
        i.value = i.props.join(',');
    }
    if (pt((e = hn(i.children, n)))) {
      return (i.return = i.value + '{' + e + '}');
    } else {
      return '';
    }
  }
  function C0(i) {
    var t = vo(i);
    return function (e, n, r, o) {
      var s = '';
      for (var a = 0; a < t; a++) {
        s += i[a](e, n, r, o) || '';
      }
      return s;
    };
  }
  function P0(i) {
    return function (t) {
      if (!t.root) {
        if ((t = t.return)) {
          i(t);
        }
      }
    };
  }
  function A0(i, t, e, n) {
    if (i.length > -1 && !i.return) {
      switch (i.type) {
        case mo:
          i.return = Iu(i.value, i.length);
          break;
        case Ru:
          return hn([kn(i, { value: be(i.value, '@', '@' + we) })], n);
        case po:
          if (i.length) {
            return g0(i.props, function (r) {
              switch (m0(r, /(::plac\w+|:read-\w+)/)) {
                case ':read-only':
                case ':read-write':
                  return hn(
                    [kn(i, { props: [be(r, /:(read-\w+)/, ':' + _i + '$1')] })],
                    n
                  );
                case '::placeholder':
                  return hn(
                    [
                      kn(i, {
                        props: [be(r, /:(plac\w+)/, ':' + we + 'input-$1')],
                      }),
                      kn(i, { props: [be(r, /:(plac\w+)/, ':' + _i + '$1')] }),
                      kn(i, { props: [be(r, /:(plac\w+)/, We + 'input-$1')] }),
                    ],
                    n
                  );
              }
              return '';
            });
          }
      }
    }
  }
  function O0(i) {
    var t = Object.create(null);
    return function (e) {
      if (t[e] === void 0) {
        t[e] = i(e);
      }
      return t[e];
    };
  }
  function et(i) {
    if (typeof i == 'object' && i !== null) {
      var t = i.$$typeof;
      switch (t) {
        case wo:
          switch (((i = i.type), i)) {
            case So:
            case Bi:
            case Ri:
            case Fi:
            case Mi:
            case Ii:
              return i;
            default:
              switch (((i = i && i.$$typeof), i)) {
                case ki:
                case Ni:
                case zi:
                case Hi:
                case Li:
                  return i;
                default:
                  return t;
              }
          }
        case bo:
          return t;
      }
    }
  }
  function Vu(i) {
    return et(i) === Bi;
  }
  function ju(i, t, e) {
    var n = '';
    e.split(' ').forEach(function (r) {
      if (i[r] === void 0) {
        n += r + ' ';
      } else {
        t.push(i[r] + ';');
      }
    });
    return n;
  }
  function G0(i) {
    var t = 0;
    var e;
    var n = 0;
    for (var r = i.length; r >= 4; ++n, r -= 4) {
      e =
        (i.charCodeAt(n) & 255) |
        ((i.charCodeAt(++n) & 255) << 8) |
        ((i.charCodeAt(++n) & 255) << 16) |
        ((i.charCodeAt(++n) & 255) << 24);
      e = (e & 65535) * 1540483477 + (((e >>> 16) * 59797) << 16);
      e ^= e >>> 24;
      t =
        ((e & 65535) * 1540483477 + (((e >>> 16) * 59797) << 16)) ^
        ((t & 65535) * 1540483477 + (((t >>> 16) * 59797) << 16));
    }
    switch (r) {
      case 3:
        t ^= (i.charCodeAt(n + 2) & 255) << 16;
      case 2:
        t ^= (i.charCodeAt(n + 1) & 255) << 8;
      case 1:
        t ^= i.charCodeAt(n) & 255;
        t = (t & 65535) * 1540483477 + (((t >>> 16) * 59797) << 16);
    }
    t ^= t >>> 13;
    t = (t & 65535) * 1540483477 + (((t >>> 16) * 59797) << 16);
    return ((t ^ (t >>> 15)) >>> 0).toString(36);
  }
  function In(i, t, e) {
    if (e == null) {
      return '';
    }
    if (e.__emotion_styles !== void 0) {
      return e;
    }
    switch (typeof e) {
      case 'boolean':
        return '';
      case 'object':
        if (e.anim === 1) {
          gt = { name: e.name, styles: e.styles, next: gt };
          return e.name;
        }
        if (e.styles !== void 0) {
          var n = e.next;
          if (n !== void 0) {
            while (n !== void 0) {
              gt = { name: n.name, styles: n.styles, next: gt };
              n = n.next;
            }
          }
          var r = e.styles + ';';
          return r;
        }
        return Z0(i, t, e);
      case 'function':
        if (i !== void 0) {
          var o = gt;
          var s = e(i);
          gt = o;
          return In(i, t, s);
        }
        break;
    }
    if (t == null) {
      return e;
    }
    var a = t[e];
    if (a === void 0) {
      return e;
    } else {
      return a;
    }
  }
  function Z0(i, t, e) {
    var n = '';
    if (Array.isArray(e)) {
      for (var r = 0; r < e.length; r++) {
        n += In(i, t, e[r]) + ';';
      }
    } else {
      for (var o in e) {
        var s = e[o];
        if (typeof s == 'object') {
          if (
            Array.isArray(s) &&
            typeof s[0] == 'string' &&
            (t == null || t[s[0]] === void 0)
          ) {
            for (var a = 0; a < s.length; a++) {
              if (Xu(s[a])) {
                n += Eo(o) + ':' + Yu(o, s[a]) + ';';
              }
            }
          } else {
            var l = In(i, t, s);
            switch (o) {
              case 'animation':
              case 'animationName':
                n += Eo(o) + ':' + l + ';';
                break;
              default:
                n += o + '{' + l + '}';
            }
          }
        } else if (t != null && t[s] !== void 0) {
          n += o + '{' + t[s] + '}';
        } else if (Xu(s)) {
          n += Eo(o) + ':' + Yu(o, s) + ';';
        }
      }
    }
    return n;
  }
  function Co() {
    var i = arguments.length;
    var t = new Array(i);
    for (var e = 0; e < i; e++) {
      t[e] = arguments[e];
    }
    return _o(t);
  }
  function tw(i, t, e) {
    var n = [];
    var r = ju(i, n, e);
    if (n.length < 2) {
      return e;
    } else {
      return r + t(n);
    }
  }
  function rw(i, t) {
    if (!t) {
      t = i.slice(0);
    }
    return Object.freeze(
      Object.defineProperties(i, { raw: { value: Object.freeze(t) } })
    );
  }
  function Po(i, t) {
    if (i == null) {
      return {};
    }
    var e = {};
    var n = Object.keys(i);
    var r;
    for (var o = 0; o < n.length; o++) {
      r = n[o];
      if (!(t.indexOf(r) >= 0)) {
        e[r] = i[r];
      }
    }
    return e;
  }
  function dn(i, t) {
    if (i == null) {
      return {};
    }
    var e = Po(i, t);
    var n;
    var r;
    if (Object.getOwnPropertySymbols) {
      var o = Object.getOwnPropertySymbols(i);
      for (r = 0; r < o.length; r++) {
        n = o[r];
        if (!(t.indexOf(n) >= 0)) {
          if (Object.prototype.propertyIsEnumerable.call(i, n)) {
            e[n] = i[n];
          }
        }
      }
    }
    return e;
  }
  function Ao(i) {
    Ao =
      typeof Symbol == 'function' && typeof Symbol.iterator == 'symbol'
        ? function (t) {
            return typeof t;
          }
        : function (t) {
            if (
              t &&
              typeof Symbol == 'function' &&
              t.constructor === Symbol &&
              t !== Symbol.prototype
            ) {
              return 'symbol';
            } else {
              return typeof t;
            }
          };
    return Ao(i);
  }
  function nc(i) {
    if (i && i.__esModule) {
      return i;
    } else {
      return { default: i };
    }
  }
  function sw(i, t) {
    var e = {};
    for (var n in i) {
      if (
        !(t.indexOf(n) >= 0) &&
        !!Object.prototype.hasOwnProperty.call(i, n)
      ) {
        e[n] = i[n];
      }
    }
    return e;
  }
  function aw(i, t) {
    if (!(i instanceof t)) {
      throw new TypeError('Cannot call a class as a function');
    }
  }
  function lw(i, t) {
    if (!i) {
      throw new ReferenceError(
        "this hasn't been initialised - super() hasn't been called"
      );
    }
    if (t && (typeof t == 'object' || typeof t == 'function')) {
      return t;
    } else {
      return i;
    }
  }
  function uw(i, t) {
    if (typeof t != 'function' && t !== null) {
      throw new TypeError(
        'Super expression must either be null or a function, not ' + typeof t
      );
    }
    i.prototype = Object.create(t && t.prototype, {
      constructor: {
        value: i,
        enumerable: false,
        writable: true,
        configurable: true,
      },
    });
    if (t) {
      if (Object.setPrototypeOf) {
        Object.setPrototypeOf(i, t);
      } else {
        i.__proto__ = t;
      }
    }
  }
  function Vi(i, t) {
    if (!(i instanceof t)) {
      throw new TypeError('Cannot call a class as a function');
    }
  }
  function ac(i, t) {
    for (var e = 0; e < t.length; e++) {
      var n = t[e];
      n.enumerable = n.enumerable || false;
      n.configurable = true;
      if ('value' in n) {
        n.writable = true;
      }
      Object.defineProperty(i, n.key, n);
    }
  }
  function Ui(i, t, e) {
    if (t) {
      ac(i.prototype, t);
    }
    if (e) {
      ac(i, e);
    }
    Object.defineProperty(i, 'prototype', { writable: false });
    return i;
  }
  function Wi(i, t) {
    Wi =
      Object.setPrototypeOf ||
      function (n, r) {
        n.__proto__ = r;
        return n;
      };
    return Wi(i, t);
  }
  function ji(i, t) {
    if (typeof t != 'function' && t !== null) {
      throw new TypeError('Super expression must either be null or a function');
    }
    i.prototype = Object.create(t && t.prototype, {
      constructor: { value: i, writable: true, configurable: true },
    });
    Object.defineProperty(i, 'prototype', { writable: false });
    if (t) {
      Wi(i, t);
    }
  }
  function St(i, t, e) {
    if (t in i) {
      Object.defineProperty(i, t, {
        value: e,
        enumerable: true,
        configurable: true,
        writable: true,
      });
    } else {
      i[t] = e;
    }
    return i;
  }
  function fw(i, t, e) {
    if (t in i) {
      Object.defineProperty(i, t, {
        value: e,
        enumerable: true,
        configurable: true,
        writable: true,
      });
    } else {
      i[t] = e;
    }
    return i;
  }
  function lc(i, t) {
    var e = Object.keys(i);
    if (Object.getOwnPropertySymbols) {
      var n = Object.getOwnPropertySymbols(i);
      if (t) {
        n = n.filter(function (r) {
          return Object.getOwnPropertyDescriptor(i, r).enumerable;
        });
      }
      e.push.apply(e, n);
    }
    return e;
  }
  function Ne(i) {
    for (var t = 1; t < arguments.length; t++) {
      var e = arguments[t] != null ? arguments[t] : {};
      if (t % 2) {
        lc(Object(e), true).forEach(function (n) {
          fw(i, n, e[n]);
        });
      } else if (Object.getOwnPropertyDescriptors) {
        Object.defineProperties(i, Object.getOwnPropertyDescriptors(e));
      } else {
        lc(Object(e)).forEach(function (n) {
          Object.defineProperty(i, n, Object.getOwnPropertyDescriptor(e, n));
        });
      }
    }
    return i;
  }
  function Gi(i) {
    Gi = Object.setPrototypeOf
      ? Object.getPrototypeOf
      : function (e) {
          return e.__proto__ || Object.getPrototypeOf(e);
        };
    return Gi(i);
  }
  function pw() {
    if (
      typeof Reflect == 'undefined' ||
      !Reflect.construct ||
      Reflect.construct.sham
    ) {
      return false;
    }
    if (typeof Proxy == 'function') {
      return true;
    }
    try {
      Date.prototype.toString.call(Reflect.construct(Date, [], function () {}));
      return true;
    } catch {
      return false;
    }
  }
  function mw(i) {
    if (i === void 0) {
      throw new ReferenceError(
        "this hasn't been initialised - super() hasn't been called"
      );
    }
    return i;
  }
  function gw(i, t) {
    if (t && (typeof t == 'object' || typeof t == 'function')) {
      return t;
    } else {
      return mw(i);
    }
  }
  function qi(i) {
    var t = pw();
    return function () {
      var n = Gi(i);
      var r;
      if (t) {
        var o = Gi(this).constructor;
        r = Reflect.construct(n, arguments, o);
      } else {
        r = n.apply(this, arguments);
      }
      return gw(this, r);
    };
  }
  function vw(i, t) {
    if (t) {
      if (t[0] === '-') {
        return i + t;
      } else {
        return i + '__' + t;
      }
    } else {
      return i;
    }
  }
  function yw(i, t, e) {
    var n = [e];
    if (t && i) {
      for (var r in t) {
        if (t.hasOwnProperty(r) && t[r]) {
          n.push(''.concat(vw(i, r)));
        }
      }
    }
    return n
      .filter(function (o) {
        return o;
      })
      .map(function (o) {
        return String(o).trim();
      })
      .join(' ');
  }
  function Ro(i) {
    return [document.documentElement, document.body, window].indexOf(i) > -1;
  }
  function hc(i) {
    if (Ro(i)) {
      return window.pageYOffset;
    } else {
      return i.scrollTop;
    }
  }
  function Yi(i, t) {
    if (Ro(i)) {
      window.scrollTo(0, t);
      return;
    }
    i.scrollTop = t;
  }
  function ww(i) {
    var t = getComputedStyle(i);
    var e = t.position === 'absolute';
    var n = /(auto|scroll)/;
    var r = document.documentElement;
    if (t.position === 'fixed') {
      return r;
    }
    for (var o = i; (o = o.parentElement); ) {
      t = getComputedStyle(o);
      if (
        (!e || t.position !== 'static') &&
        n.test(t.overflow + t.overflowY + t.overflowX)
      ) {
        return o;
      }
    }
    return r;
  }
  function bw(i, t, e, n) {
    return e * ((i = i / n - 1) * i * i + 1) + t;
  }
  function Zi(i, t) {
    function l() {
      a += s;
      var u = bw(a, r, o, e);
      Yi(i, u);
      if (a < e) {
        window.requestAnimationFrame(l);
      } else {
        n(i);
      }
    }
    var e =
      arguments.length > 2 && arguments[2] !== void 0 ? arguments[2] : 200;
    var n = arguments.length > 3 && arguments[3] !== void 0 ? arguments[3] : Xi;
    var r = hc(i);
    var o = t - r;
    var s = 10;
    var a = 0;
    l();
  }
  function Sw(i, t) {
    var e = i.getBoundingClientRect();
    var n = t.getBoundingClientRect();
    var r = t.offsetHeight / 3;
    if (n.bottom + r > e.bottom) {
      Yi(
        i,
        Math.min(
          t.offsetTop + t.clientHeight - i.offsetHeight + r,
          i.scrollHeight
        )
      );
    } else if (n.top - r < e.top) {
      Yi(i, Math.max(t.offsetTop - r, 0));
    }
  }
  function Ew(i) {
    var t = i.getBoundingClientRect();
    return {
      bottom: t.bottom,
      height: t.height,
      left: t.left,
      right: t.right,
      top: t.top,
      width: t.width,
    };
  }
  function dc() {
    try {
      document.createEvent('TouchEvent');
      return true;
    } catch {
      return false;
    }
  }
  function _w() {
    try {
      return /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(
        navigator.userAgent
      );
    } catch {
      return false;
    }
  }
  function Cw(i) {
    var t = i.maxHeight;
    var e = i.menuEl;
    var n = i.minHeight;
    var r = i.placement;
    var o = i.shouldScroll;
    var s = i.isFixedPosition;
    var a = i.theme;
    var l = a.spacing;
    var u = ww(e);
    var c = { placement: 'bottom', maxHeight: t };
    if (!e || !e.offsetParent) {
      return c;
    }
    var h = u.getBoundingClientRect();
    var d = h.height;
    var g = e.getBoundingClientRect();
    var y = g.bottom;
    var x = g.height;
    var b = g.top;
    var T = e.offsetParent.getBoundingClientRect();
    var f = T.top;
    var E = window.innerHeight;
    var A = hc(u);
    var C = parseInt(getComputedStyle(e).marginBottom, 10);
    var O = parseInt(getComputedStyle(e).marginTop, 10);
    var D = f - O;
    var N = E - b;
    var B = D + A;
    var Z = d - A - b;
    var Y = y - E + A + C;
    var U = A + b - O;
    var K = 160;
    switch (r) {
      case 'auto':
      case 'bottom':
        if (N >= x) {
          return { placement: 'bottom', maxHeight: t };
        }
        if (Z >= x && !s) {
          if (o) {
            Zi(u, Y, K);
          }
          return { placement: 'bottom', maxHeight: t };
        }
        if ((!s && Z >= n) || (s && N >= n)) {
          if (o) {
            Zi(u, Y, K);
          }
          var Q = s ? N - C : Z - C;
          return { placement: 'bottom', maxHeight: Q };
        }
        if (r === 'auto' || s) {
          var le = t;
          var re = s ? D : B;
          if (re >= n) {
            le = Math.min(re - C - l.controlHeight, t);
          }
          return { placement: 'top', maxHeight: le };
        }
        if (r === 'bottom') {
          if (o) {
            Yi(u, Y);
          }
          return { placement: 'bottom', maxHeight: t };
        }
        break;
      case 'top':
        if (D >= x) {
          return { placement: 'top', maxHeight: t };
        }
        if (B >= x && !s) {
          if (o) {
            Zi(u, U, K);
          }
          return { placement: 'top', maxHeight: t };
        }
        if ((!s && B >= n) || (s && D >= n)) {
          var se = t;
          if ((!s && B >= n) || (s && D >= n)) {
            se = s ? D - O : B - O;
          }
          if (o) {
            Zi(u, U, K);
          }
          return { placement: 'top', maxHeight: se };
        }
        return { placement: 'bottom', maxHeight: t };
      default:
        throw new Error('Invalid placement provided "'.concat(r, '".'));
    }
    return c;
  }
  function Pw(i) {
    var t = { bottom: 'top', top: 'bottom' };
    if (i) {
      return t[i];
    } else {
      return 'bottom';
    }
  }
  function hb(i) {
    var t = i.children;
    var e = i.innerProps;
    return ie('div', e, t || ie(Fo, { size: 14 }));
  }
  function ko(i, t) {
    if (t == null || t > i.length) {
      t = i.length;
    }
    var e = 0;
    for (var n = new Array(t); e < t; e++) {
      n[e] = i[e];
    }
    return n;
  }
  function bb(i) {
    if (Array.isArray(i)) {
      return ko(i);
    }
  }
  function Sb(i) {
    if (
      (typeof Symbol != 'undefined' && i[Symbol.iterator] != null) ||
      i['@@iterator'] != null
    ) {
      return Array.from(i);
    }
  }
  function Eb(i, t) {
    if (i) {
      if (typeof i == 'string') {
        return ko(i, t);
      }
      var e = Object.prototype.toString.call(i).slice(8, -1);
      if (e === 'Object' && i.constructor) {
        e = i.constructor.name;
      }
      if (e === 'Map' || e === 'Set') {
        return Array.from(i);
      }
      if (
        e === 'Arguments' ||
        /^(?:Ui|I)nt(?:8|16|32)(?:Clamped)?Array$/.test(e)
      ) {
        return ko(i, t);
      }
    }
  }
  function _b() {
    throw new TypeError(`Invalid attempt to spread non-iterable instance.
In order to be iterable, non-array objects must have a [Symbol.iterator]() method.`);
  }
  function Cc(i) {
    return bb(i) || Sb(i) || Eb(i) || _b();
  }
  function xb(i, t) {
    return i === t || (!!Pc(i) && !!Pc(t));
  }
  function Tb(i, t) {
    if (i.length !== t.length) {
      return false;
    }
    for (var e = 0; e < i.length; e++) {
      if (!xb(i[e], t[e])) {
        return false;
      }
    }
    return true;
  }
  function Cb(i, t) {
    function s() {
      var a = [];
      for (var l = 0; l < arguments.length; l++) {
        a[l] = arguments[l];
      }
      if (!o || e !== this || !t(a, n)) {
        r = i.apply(this, a);
        o = true;
        e = this;
        n = a;
      }
      return r;
    }
    if (t === void 0) {
      t = Tb;
    }
    var e;
    var n = [];
    var r;
    var o = false;
    return s;
  }
  function kb(i) {
    i.in;
    i.out;
    i.onExited;
    i.appear;
    i.enter;
    i.exit;
    var t = i.innerRef;
    i.emotion;
    var e = dn(i, [
      'in',
      'out',
      'onExited',
      'appear',
      'enter',
      'exit',
      'innerRef',
      'emotion',
    ]);
    return ie(
      'input',
      ne({ ref: t }, e, {
        css: Co(
          {
            label: 'dummyInput',
            background: 0,
            border: 0,
            fontSize: 'inherit',
            outline: 0,
            padding: 0,
            width: 1,
            color: 'transparent',
            left: -100,
            opacity: 0,
            position: 'relative',
            transform: 'scale(0)',
          },
          '',
          ''
        ),
      })
    );
  }
  function Nb(i) {
    var t = i.isEnabled;
    var e = i.onBottomArrive;
    var n = i.onBottomLeave;
    var r = i.onTopArrive;
    var o = i.onTopLeave;
    var s = ut(false);
    var a = ut(false);
    var l = ut(0);
    var u = ut(null);
    var c = ct(function (b, T) {
      if (u.current !== null) {
        var f = u.current;
        var E = f.scrollTop;
        var A = f.scrollHeight;
        var C = f.clientHeight;
        var O = u.current;
        var D = T > 0;
        var N = A - C - E;
        var B = false;
        if (N > T && s.current) {
          if (n) {
            n(b);
          }
          s.current = false;
        }
        if (D && a.current) {
          if (o) {
            o(b);
          }
          a.current = false;
        }
        if (D && T > N) {
          if (e && !s.current) {
            e(b);
          }
          O.scrollTop = A;
          B = true;
          s.current = true;
        } else if (!D && -T > E) {
          if (r && !a.current) {
            r(b);
          }
          O.scrollTop = 0;
          B = true;
          a.current = true;
        }
        if (B) {
          Bb(b);
        }
      }
    }, []);
    var h = ct(
      function (b) {
        c(b, b.deltaY);
      },
      [c]
    );
    var d = ct(function (b) {
      l.current = b.changedTouches[0].clientY;
    }, []);
    var g = ct(
      function (b) {
        var T = l.current - b.changedTouches[0].clientY;
        c(b, T);
      },
      [c]
    );
    var y = ct(
      function (b) {
        if (b) {
          var T = Tw ? { passive: false } : false;
          if (typeof b.addEventListener == 'function') {
            b.addEventListener('wheel', h, T);
          }
          if (typeof b.addEventListener == 'function') {
            b.addEventListener('touchstart', d, T);
          }
          if (typeof b.addEventListener == 'function') {
            b.addEventListener('touchmove', g, T);
          }
        }
      },
      [g, d, h]
    );
    var x = ct(
      function (b) {
        if (b) {
          if (typeof b.removeEventListener == 'function') {
            b.removeEventListener('wheel', h, false);
          }
          if (typeof b.removeEventListener == 'function') {
            b.removeEventListener('touchstart', d, false);
          }
          if (typeof b.removeEventListener == 'function') {
            b.removeEventListener('touchmove', g, false);
          }
        }
      },
      [g, d, h]
    );
    kt(
      function () {
        if (t) {
          var b = u.current;
          y(b);
          return function () {
            x(b);
          };
        }
      },
      [t, y, x]
    );
    return function (b) {
      u.current = b;
    };
  }
  function Fc(i) {
    i.preventDefault();
  }
  function Lc(i) {
    i.stopPropagation();
  }
  function kc() {
    var i = this.scrollTop;
    var t = this.scrollHeight;
    var e = i + this.offsetHeight;
    if (i === 0) {
      this.scrollTop = 1;
    } else if (e === t) {
      this.scrollTop = i - 1;
    }
  }
  function Bc() {
    return 'ontouchstart' in window || navigator.maxTouchPoints;
  }
  function Ib(i) {
    var t = i.isEnabled;
    var e = i.accountForScrollbars;
    var n = e === void 0 ? true : e;
    var r = ut({});
    var o = ut(null);
    var s = ct(function (l) {
      if (Nc) {
        var u = document.body;
        var c = u && u.style;
        if (n) {
          Rc.forEach(function (y) {
            var x = c && c[y];
            r.current[y] = x;
          });
        }
        if (n && zn < 1) {
          var h = parseInt(r.current.paddingRight, 10) || 0;
          var d = document.body ? document.body.clientWidth : 0;
          var g = window.innerWidth - d + h || 0;
          Object.keys(Mc).forEach(function (y) {
            var x = Mc[y];
            if (c) {
              c[y] = x;
            }
          });
          if (c) {
            c.paddingRight = ''.concat(g, 'px');
          }
        }
        if (u && Bc()) {
          u.addEventListener('touchmove', Fc, fn);
          if (l) {
            l.addEventListener('touchstart', kc, fn);
            l.addEventListener('touchmove', Lc, fn);
          }
        }
        zn += 1;
      }
    }, []);
    var a = ct(function (l) {
      if (Nc) {
        var u = document.body;
        var c = u && u.style;
        zn = Math.max(zn - 1, 0);
        if (n && zn < 1) {
          Rc.forEach(function (h) {
            var d = r.current[h];
            if (c) {
              c[h] = d;
            }
          });
        }
        if (u && Bc()) {
          u.removeEventListener('touchmove', Fc, fn);
          if (l) {
            l.removeEventListener('touchstart', kc, fn);
            l.removeEventListener('touchmove', Lc, fn);
          }
        }
      }
    }, []);
    kt(
      function () {
        if (t) {
          var l = o.current;
          s(l);
          return function () {
            a(l);
          };
        }
      },
      [t, s, a]
    );
    return function (l) {
      o.current = l;
    };
  }
  function Vb(i) {
    var t = i.children;
    var e = i.lockEnabled;
    var n = i.captureEnabled;
    var r = n === void 0 ? true : n;
    var o = i.onBottomArrive;
    var s = i.onBottomLeave;
    var a = i.onTopArrive;
    var l = i.onTopLeave;
    var u = Nb({
      isEnabled: r,
      onBottomArrive: o,
      onBottomLeave: s,
      onTopArrive: a,
      onTopLeave: l,
    });
    var c = Ib({ isEnabled: e });
    var h = function (g) {
      u(g);
      c(g);
    };
    return ie(L.Fragment, null, e && ie('div', { onClick: Hb, css: zb }), t(h));
  }
  function Hc(i, t, e, n) {
    var r = jc(i, t, e);
    var o = Gc(i, t, e);
    var s = Wc(i, t);
    var a = Ji(i, t);
    return {
      type: 'option',
      data: t,
      isDisabled: r,
      isSelected: o,
      label: s,
      value: a,
      index: n,
    };
  }
  function zc(i, t) {
    return i.options
      .map(function (e, n) {
        if (e.options) {
          var r = e.options
            .map(function (s, a) {
              return Hc(i, s, t, a);
            })
            .filter(function (s) {
              return Uc(i, s);
            });
          if (r.length > 0) {
            return { type: 'group', data: e, options: r, index: n };
          } else {
            return;
          }
        }
        var o = Hc(i, e, t, n);
        if (Uc(i, o)) {
          return o;
        } else {
          return;
        }
      })
      .filter(function (e) {
        return !!e;
      });
  }
  function Vc(i) {
    return i.reduce(function (t, e) {
      if (e.type === 'group') {
        t.push.apply(
          t,
          Cc(
            e.options.map(function (n) {
              return n.data;
            })
          )
        );
      } else {
        t.push(e.data);
      }
      return t;
    }, []);
  }
  function $b(i, t) {
    return Vc(zc(i, t));
  }
  function Uc(i, t) {
    var e = i.inputValue;
    var n = e === void 0 ? '' : e;
    var r = t.data;
    var o = t.isSelected;
    var s = t.label;
    var a = t.value;
    return (!Xc(i) || !o) && qc(i, { label: s, value: a, data: r }, n);
  }
  function eS(i, t) {
    var e = i.focusedValue;
    var n = i.selectValue;
    var r = n.indexOf(e);
    if (r > -1) {
      var o = t.indexOf(e);
      if (o > -1) {
        return e;
      }
      if (r < t.length) {
        return t[r];
      }
    }
    return null;
  }
  function tS(i, t) {
    var e = i.focusedOption;
    if (e && t.indexOf(e) > -1) {
      return e;
    } else {
      return t[0];
    }
  }
  function jc(i, t, e) {
    if (typeof i.isOptionDisabled == 'function') {
      return i.isOptionDisabled(t, e);
    } else {
      return false;
    }
  }
  function Gc(i, t, e) {
    if (e.indexOf(t) > -1) {
      return true;
    }
    if (typeof i.isOptionSelected == 'function') {
      return i.isOptionSelected(t, e);
    }
    var n = Ji(i, t);
    return e.some(function (r) {
      return Ji(i, r) === n;
    });
  }
  function qc(i, t, e) {
    if (i.filterOption) {
      return i.filterOption(t, e);
    } else {
      return true;
    }
  }
  function eh(i, t) {
    i.prototype = Object.create(t.prototype);
    i.prototype.constructor = i;
    Wi(i, t);
  }
  function fS(i, t) {
    if (i.classList) {
      return !!t && i.classList.contains(t);
    } else {
      return (
        (' ' + (i.className.baseVal || i.className) + ' ').indexOf(
          ' ' + t + ' '
        ) !== -1
      );
    }
  }
  function pS(i, t) {
    if (i.classList) {
      i.classList.add(t);
    } else if (!fS(i, t)) {
      if (typeof i.className == 'string') {
        i.className = i.className + ' ' + t;
      } else {
        i.setAttribute(
          'class',
          ((i.className && i.className.baseVal) || '') + ' ' + t
        );
      }
    }
  }
  function th(i, t) {
    return i
      .replace(new RegExp('(^|\\s)' + t + '(?:\\s|$)', 'g'), '$1')
      .replace(/\s+/g, ' ')
      .replace(/^\s*|\s*$/g, '');
  }
  function mS(i, t) {
    if (i.classList) {
      i.classList.remove(t);
    } else if (typeof i.className == 'string') {
      i.className = th(i.className, t);
    } else {
      i.setAttribute(
        'class',
        th((i.className && i.className.baseVal) || '', t)
      );
    }
  }
  function mn() {}
  function OS() {
    if (!Qi) {
      Qi =
        (typeof crypto != 'undefined' &&
          crypto.getRandomValues &&
          crypto.getRandomValues.bind(crypto)) ||
        (typeof msCrypto != 'undefined' &&
          typeof msCrypto.getRandomValues == 'function' &&
          msCrypto.getRandomValues.bind(msCrypto));
      if (!Qi) {
        throw new Error(
          'crypto.getRandomValues() not supported. See https://github.com/uuidjs/uuid#getrandomvalues-not-supported'
        );
      }
    }
    return Qi(AS);
  }
  function RS(i) {
    return typeof i == 'string' && DS.test(i);
  }
  function MS(i) {
    var t = arguments.length > 1 && arguments[1] !== void 0 ? arguments[1] : 0;
    var e = (
      Ie[i[t + 0]] +
      Ie[i[t + 1]] +
      Ie[i[t + 2]] +
      Ie[i[t + 3]] +
      '-' +
      Ie[i[t + 4]] +
      Ie[i[t + 5]] +
      '-' +
      Ie[i[t + 6]] +
      Ie[i[t + 7]] +
      '-' +
      Ie[i[t + 8]] +
      Ie[i[t + 9]] +
      '-' +
      Ie[i[t + 10]] +
      Ie[i[t + 11]] +
      Ie[i[t + 12]] +
      Ie[i[t + 13]] +
      Ie[i[t + 14]] +
      Ie[i[t + 15]]
    ).toLowerCase();
    if (!RS(e)) {
      throw TypeError('Stringified UUID is invalid');
    }
    return e;
  }
  function uh(i, t, e) {
    i = i || {};
    var n = i.random || (i.rng || OS)();
    n[6] = (n[6] & 15) | 64;
    n[8] = (n[8] & 63) | 128;
    if (t) {
      e = e || 0;
      for (var r = 0; r < 16; ++r) {
        t[e + r] = n[r];
      }
      return t;
    }
    return MS(n);
  }
  function lE(i, t, e, n, r) {
    xh(i, t, e || 0, n || i.length - 1, r || uE);
  }
  function xh(i, t, e, n, r) {
    while (n > e) {
      if (n - e > 600) {
        var o = n - e + 1;
        var s = t - e + 1;
        var a = Math.log(o);
        var l = 0.5 * Math.exp((2 * a) / 3);
        var u =
          0.5 * Math.sqrt((a * l * (o - l)) / o) * (s - o / 2 < 0 ? -1 : 1);
        var c = Math.max(e, Math.floor(t - (s * l) / o + u));
        var h = Math.min(n, Math.floor(t + ((o - s) * l) / o + u));
        xh(i, t, c, h, r);
      }
      var d = i[t];
      var g = e;
      var y = n;
      Wn(i, e, t);
      for (r(i[n], d) > 0 && Wn(i, e, n); g < y; ) {
        Wn(i, g, y);
        g++;
        for (y--; r(i[g], d) < 0; ) {
          g++;
        }
        while (r(i[y], d) > 0) {
          y--;
        }
      }
      if (r(i[e], d) === 0) {
        Wn(i, e, y);
      } else {
        y++;
        Wn(i, y, n);
      }
      if (y <= t) {
        e = y + 1;
      }
      if (t <= y) {
        n = y - 1;
      }
    }
  }
  function Wn(i, t, e) {
    var n = i[t];
    i[t] = i[e];
    i[e] = n;
  }
  function uE(i, t) {
    if (i < t) {
      return -1;
    } else if (i > t) {
      return 1;
    } else {
      return 0;
    }
  }
  function hE(i, t, e) {
    if (!e) {
      return t.indexOf(i);
    }
    for (let n = 0; n < t.length; n++) {
      if (e(i, t[n])) {
        return n;
      }
    }
    return -1;
  }
  function vn(i, t) {
    jn(i, 0, i.children.length, t, i);
  }
  function jn(i, t, e, n, r) {
    if (!r) {
      r = yn(null);
    }
    r.minX = 1 / 0;
    r.minY = 1 / 0;
    r.maxX = -1 / 0;
    r.maxY = -1 / 0;
    for (let o = t; o < e; o++) {
      const s = i.children[o];
      Gn(r, i.leaf ? n(s) : s);
    }
    return r;
  }
  function Gn(i, t) {
    i.minX = Math.min(i.minX, t.minX);
    i.minY = Math.min(i.minY, t.minY);
    i.maxX = Math.max(i.maxX, t.maxX);
    i.maxY = Math.max(i.maxY, t.maxY);
    return i;
  }
  function dE(i, t) {
    return i.minX - t.minX;
  }
  function fE(i, t) {
    return i.minY - t.minY;
  }
  function as(i) {
    return (i.maxX - i.minX) * (i.maxY - i.minY);
  }
  function or(i) {
    return i.maxX - i.minX + (i.maxY - i.minY);
  }
  function pE(i, t) {
    return (
      (Math.max(t.maxX, i.maxX) - Math.min(t.minX, i.minX)) *
      (Math.max(t.maxY, i.maxY) - Math.min(t.minY, i.minY))
    );
  }
  function mE(i, t) {
    const e = Math.max(i.minX, t.minX);
    const n = Math.max(i.minY, t.minY);
    const r = Math.min(i.maxX, t.maxX);
    const o = Math.min(i.maxY, t.maxY);
    return Math.max(0, r - e) * Math.max(0, o - n);
  }
  function ls(i, t) {
    return (
      i.minX <= t.minX &&
      i.minY <= t.minY &&
      t.maxX <= i.maxX &&
      t.maxY <= i.maxY
    );
  }
  function sr(i, t) {
    return (
      t.minX <= i.maxX &&
      t.minY <= i.maxY &&
      t.maxX >= i.minX &&
      t.maxY >= i.minY
    );
  }
  function yn(i) {
    return {
      children: i,
      height: 1,
      leaf: true,
      minX: 1 / 0,
      minY: 1 / 0,
      maxX: -1 / 0,
      maxY: -1 / 0,
    };
  }
  function Th(i, t, e, n, r) {
    const o = [t, e];
    while (o.length) {
      e = o.pop();
      t = o.pop();
      if (e - t <= n) {
        continue;
      }
      const s = t + Math.ceil((e - t) / n / 2) * n;
      lE(i, s, t, e, r);
      o.push(t, s, s, e);
    }
  }
  function zE(i, t, e, n, r) {
    var o;
    var s;
    var a = {};
    for (s in t) {
      if (s == 'ref') {
        o = t[s];
      } else {
        a[s] = t[s];
      }
    }
    var l = {
      type: i,
      props: a,
      key: e,
      ref: o,
      __k: null,
      __: null,
      __b: 0,
      __e: null,
      __d: void 0,
      __c: null,
      __h: null,
      constructor: void 0,
      __v: --HE,
      __source: n,
      __self: r,
    };
    if (typeof i == 'function' && (o = i.defaultProps)) {
      for (s in o) {
        if (a[s] === void 0) {
          a[s] = o[s];
        }
      }
    }
    if (Fh.options.vnode) {
      Fh.options.vnode(l);
    }
    return l;
  }
  var fs;
  var qn = {};
  var ms = [];
  var zh = /acit|ex(?:s|g|n|p|$)|rph|grid|ows|mnc|ntw|ine[ch]|zoo|^ord|itera/i;
  var ye = ms.slice;
  var W = {
    __e: function (i, t) {
      var e;
      var n;
      for (var r; (t = t.__); ) {
        if ((e = t.__c) && !e.__) {
          try {
            if ((n = e.constructor) && n.getDerivedStateFromError != null) {
              e.setState(n.getDerivedStateFromError(i));
              r = e.__d;
            }
            if (e.componentDidCatch != null) {
              e.componentDidCatch(i);
              r = e.__d;
            }
            if (r) {
              return (e.__E = e);
            }
          } catch (o) {
            i = o;
          }
        }
      }
      throw i;
    },
  };
  var Pe = 0;
  var ot = function (i) {
    return i != null && i.constructor === void 0;
  };
  Oe.prototype.setState = function (i, t) {
    var e =
      this.__s != null && this.__s !== this.state
        ? this.__s
        : (this.__s = vt({}, this.state));
    if (typeof i == 'function') {
      i = i(vt({}, e), this.props);
    }
    if (i) {
      vt(e, i);
    }
    if (i != null && this.__v) {
      if (t) {
        this.__h.push(t);
      }
      ur(this);
    }
  };
  Oe.prototype.forceUpdate = function (i) {
    if (this.__v) {
      this.__e = true;
      if (i) {
        this.__h.push(i);
      }
      ur(this);
    }
  };
  Oe.prototype.render = Ye;
  var bn = [];
  var ds =
    typeof Promise == 'function'
      ? Promise.prototype.then.bind(Promise.resolve())
      : setTimeout;
  Yn.__r = 0;
  var ps = 0;
  var jh = Object.freeze({
    __proto__: null,
    [Symbol.toStringTag]: 'Module',
    render: Xt,
    hydrate: hr,
    createElement: Ae,
    h: Ae,
    Fragment: Ye,
    createRef: Xn,
    get isValidElement() {
      return ot;
    },
    Component: Oe,
    cloneElement: Ps,
    createContext: Yt,
    toChildArray: lt,
    get options() {
      return W;
    },
  });
  var Lt;
  var Be;
  var As;
  var Zt = 0;
  var dr = [];
  var Os = W.__b;
  var Ds = W.__r;
  var Rs = W.diffed;
  var Ms = W.__c;
  var Fs = W.unmount;
  W.__b = function (i) {
    Be = null;
    if (Os) {
      Os(i);
    }
  };
  W.__r = function (i) {
    if (Ds) {
      Ds(i);
    }
    Lt = 0;
    var t = (Be = i.__c).__H;
    if (t) {
      t.__h.forEach(Kn);
      t.__h.forEach(mr);
      t.__h = [];
    }
  };
  W.diffed = function (i) {
    if (Rs) {
      Rs(i);
    }
    var t = i.__c;
    if (t && t.__H && t.__H.__h.length) {
      if (dr.push(t) === 1 || As !== W.requestAnimationFrame) {
        (
          (As = W.requestAnimationFrame) ||
          function (e) {
            var n;
            var r = function () {
              clearTimeout(o);
              if (Bs) {
                cancelAnimationFrame(n);
              }
              setTimeout(e);
            };
            var o = setTimeout(r, 100);
            if (Bs) {
              n = requestAnimationFrame(r);
            }
          }
        )(qh);
      }
    }
    Be = null;
  };
  W.__c = function (i, t) {
    t.some(function (e) {
      try {
        e.__h.forEach(Kn);
        e.__h = e.__h.filter(function (n) {
          return !n.__ || mr(n);
        });
      } catch (n) {
        t.some(function (r) {
          if (r.__h) {
            r.__h = [];
          }
        });
        t = [];
        W.__e(n, e.__v);
      }
    });
    if (Ms) {
      Ms(i, t);
    }
  };
  W.unmount = function (i) {
    if (Fs) {
      Fs(i);
    }
    var t;
    var e = i.__c;
    if (e && e.__H) {
      e.__H.__.forEach(function (n) {
        try {
          Kn(n);
        } catch (r) {
          t = r;
        }
      });
      if (t) {
        W.__e(t, e.__v);
      }
    }
  };
  var Bs = typeof requestAnimationFrame == 'function';
  (_n.prototype = new Oe()).isPureReactComponent = true;
  _n.prototype.shouldComponentUpdate = function (i, t) {
    return vr(this.props, i) || vr(this.state, t);
  };
  var zs = W.__b;
  W.__b = function (i) {
    if (i.type && i.type.__f && i.ref) {
      i.props.ref = i.ref;
      i.ref = null;
    }
    if (zs) {
      zs(i);
    }
  };
  var Xh =
    (typeof Symbol != 'undefined' &&
      Symbol.for &&
      Symbol.for('react.forward_ref')) ||
    3911;
  var Vs = function (i, t) {
    if (i == null) {
      return null;
    } else {
      return lt(lt(i).map(t));
    }
  };
  var Us = {
    map: Vs,
    forEach: Vs,
    count: function (i) {
      if (i) {
        return lt(i).length;
      } else {
        return 0;
      }
    },
    only: function (i) {
      var t = lt(i);
      if (t.length !== 1) {
        throw 'Children.only';
      }
      return t[0];
    },
    toArray: lt,
  };
  var Yh = W.__e;
  W.__e = function (i, t, e) {
    if (i.then) {
      var n;
      for (var r = t; (r = r.__); ) {
        if ((n = r.__c) && n.__c) {
          if (t.__e == null) {
            t.__e = e.__e;
            t.__k = e.__k;
          }
          return n.__c(i, t);
        }
      }
    }
    Yh(i, t, e);
  };
  var Ws = W.unmount;
  W.unmount = function (i) {
    var t = i.__c;
    if (t && t.__R) {
      t.__R();
    }
    if (t && i.__h === true) {
      i.type = null;
    }
    if (Ws) {
      Ws(i);
    }
  };
  (xn.prototype = new Oe()).__c = function (i, t) {
    var e = t.__c;
    var n = this;
    if (n.t == null) {
      n.t = [];
    }
    n.t.push(e);
    var r = js(n.__v);
    var o = false;
    var s = function () {
      if (!o) {
        o = true;
        e.__R = null;
        if (r) {
          r(a);
        } else {
          a();
        }
      }
    };
    e.__R = s;
    var a = function () {
      if (!--n.__u) {
        if (n.state.__e) {
          var u = n.state.__e;
          n.__v.__k[0] = (function h() {
            var d = u;
            var g = u.__c.__P;
            var y = u.__c.__O;
            if (d) {
              d.__v = null;
              d.__k =
                d.__k &&
                d.__k.map(function (x) {
                  return h(x, g, y);
                });
              if (d.__c && d.__c.__P === g) {
                if (d.__e) {
                  y.insertBefore(d.__e, d.__d);
                }
                d.__c.__e = true;
                d.__c.__P = y;
              }
            }
            return d;
          })();
        }
        var c;
        for (n.setState({ __e: (n.__b = null) }); (c = n.t.pop()); ) {
          c.forceUpdate();
        }
      }
    };
    var l = t.__h === true;
    if (!n.__u++ && !l) {
      n.setState({ __e: (n.__b = n.__v.__k[0]) });
    }
    i.then(s, s);
  };
  xn.prototype.componentWillUnmount = function () {
    this.t = [];
  };
  xn.prototype.render = function (i, t) {
    if (this.__b) {
      if (this.__v.__k) {
        var e = document.createElement('div');
        var n = this.__v.__k[0].__c;
        this.__v.__k[0] = (function o(s, a, l) {
          if (s) {
            if (s.__c && s.__c.__H) {
              s.__c.__H.__.forEach(function (u) {
                if (typeof u.__c == 'function') {
                  u.__c();
                }
              });
              s.__c.__H = null;
            }
            if ((s = Is({}, s)).__c != null) {
              if (s.__c.__P === l) {
                s.__c.__P = a;
              }
              s.__c = null;
            }
            s.__k =
              s.__k &&
              s.__k.map(function (u) {
                return o(u, a, l);
              });
          }
          return s;
        })(this.__b, e, (n.__O = n.__P));
      }
      this.__b = null;
    }
    var r = t.__e && Ae(Ye, null, i.fallback);
    if (r) {
      r.__h = null;
    }
    return [Ae(Ye, null, t.__e ? null : i.children), r];
  };
  var qs = function (i, t, e) {
    if (++e[1] === e[0]) {
      i.o.delete(t);
    }
    if (i.props.revealOrder && (i.props.revealOrder[0] !== 't' || !i.o.size)) {
      for (e = i.u; e; ) {
        while (e.length > 3) {
          e.pop()();
        }
        if (e[1] < e[0]) {
          break;
        }
        i.u = e = e[2];
      }
    }
  };
  (Jt.prototype = new Oe()).__e = function (i) {
    var t = this;
    var e = js(t.__v);
    var n = t.o.get(i);
    n[0]++;
    return function (r) {
      var o = function () {
        if (t.props.revealOrder) {
          n.push(r);
          qs(t, i, n);
        } else {
          r();
        }
      };
      if (e) {
        e(o);
      } else {
        o();
      }
    };
  };
  Jt.prototype.render = function (i) {
    this.u = null;
    this.o = new Map();
    var t = lt(i.children);
    if (i.revealOrder && i.revealOrder[0] === 'b') {
      t.reverse();
    }
    for (var e = t.length; e--; ) {
      this.o.set(t[e], (this.u = [1, 0, this.u]));
    }
    return i.children;
  };
  Jt.prototype.componentDidUpdate = Jt.prototype.componentDidMount =
    function () {
      var i = this;
      this.o.forEach(function (t, e) {
        qs(i, e, t);
      });
    };
  var Xs =
    (typeof Symbol != 'undefined' &&
      Symbol.for &&
      Symbol.for('react.element')) ||
    60103;
  var Jh =
    /^(?:accent|alignment|arabic|baseline|cap|clip(?!PathU)|color|dominant|fill|flood|font|glyph(?!R)|horiz|marker(?!H|W|U)|overline|paint|stop|strikethrough|stroke|text(?!L)|underline|unicode|units|v|vector|vert|word|writing|x(?!C))[A-Z]/;
  var Qh = typeof document != 'undefined';
  var $h = function (i) {
    return (
      typeof Symbol != 'undefined' && typeof Symbol() == 'symbol'
        ? /fil|che|rad/i
        : /fil|che|ra/i
    ).test(i);
  };
  Oe.prototype.isReactComponent = {};
  [
    'componentWillMount',
    'componentWillReceiveProps',
    'componentWillUpdate',
  ].forEach(function (i) {
    Object.defineProperty(Oe.prototype, i, {
      configurable: true,
      get: function () {
        return this['UNSAFE_' + i];
      },
      set: function (t) {
        Object.defineProperty(this, i, {
          configurable: true,
          writable: true,
          value: t,
        });
      },
    });
  });
  var Ks = W.event;
  W.event = function (i) {
    if (Ks) {
      i = Ks(i);
    }
    i.persist = ed;
    i.isPropagationStopped = td;
    i.isDefaultPrevented = nd;
    return (i.nativeEvent = i);
  };
  var Js;
  var Qs = {
    configurable: true,
    get: function () {
      return this.class;
    },
  };
  var $s = W.vnode;
  W.vnode = function (i) {
    var t = i.type;
    var e = i.props;
    var n = e;
    if (typeof t == 'string') {
      var r = t.indexOf('-') === -1;
      for (var o in ((n = {}), e)) {
        var s = e[o];
        if (
          (!Qh || o !== 'children' || t !== 'noscript') &&
          (o !== 'value' || !('defaultValue' in e) || s != null)
        ) {
          if (o === 'defaultValue' && 'value' in e && e.value == null) {
            o = 'value';
          } else if (o === 'download' && s === true) {
            s = '';
          } else if (/ondoubleclick/i.test(o)) {
            o = 'ondblclick';
          } else if (/^onchange(textarea|input)/i.test(o + t) && !$h(e.type)) {
            o = 'oninput';
          } else if (/^onfocus$/i.test(o)) {
            o = 'onfocusin';
          } else if (/^onblur$/i.test(o)) {
            o = 'onfocusout';
          } else if (/^on(Ani|Tra|Tou|BeforeInp)/.test(o)) {
            o = o.toLowerCase();
          } else if (r && Jh.test(o)) {
            o = o.replace(/[A-Z0-9]/, '-$&').toLowerCase();
          } else if (s === null) {
            s = void 0;
          }
          n[o] = s;
        }
      }
      if (t == 'select' && n.multiple && Array.isArray(n.value)) {
        n.value = lt(e.children).forEach(function (a) {
          a.props.selected = n.value.indexOf(a.props.value) != -1;
        });
      }
      if (t == 'select' && n.defaultValue != null) {
        n.value = lt(e.children).forEach(function (a) {
          a.props.selected = n.multiple
            ? n.defaultValue.indexOf(a.props.value) != -1
            : n.defaultValue == a.props.value;
        });
      }
      i.props = n;
      if (e.class != e.className) {
        Qs.enumerable = 'className' in e;
        if (e.className != null) {
          n.class = e.className;
        }
        Object.defineProperty(n, 'className', Qs);
      }
    }
    i.$$typeof = Xs;
    if ($s) {
      $s(i);
    }
  };
  var ea = W.__r;
  W.__r = function (i) {
    if (ea) {
      ea(i);
    }
    Js = i.__c;
  };
  var ta = {
    ReactCurrentDispatcher: {
      current: {
        readContext: function (i) {
          return Js.__n[i.__c].props.value;
        },
      },
    },
  };
  var id = '17.0.2';
  var sa = function (i, t) {
    return i(t);
  };
  var aa = function (i, t) {
    return i(t);
  };
  var rd = Ye;
  var L = {
    useState: yt,
    useReducer: fr,
    useEffect: kt,
    useLayoutEffect: pr,
    useRef: ut,
    useImperativeHandle: Ls,
    useMemo: wt,
    useCallback: ct,
    useContext: En,
    useDebugValue: ks,
    version: '17.0.2',
    Children: Us,
    render: Ys,
    hydrate: Zs,
    unmountComponentAtNode: ra,
    createPortal: wr,
    createElement: Ae,
    createContext: Yt,
    createFactory: na,
    cloneElement: ia,
    createRef: Xn,
    Fragment: Ye,
    isValidElement: br,
    findDOMNode: oa,
    Component: Oe,
    PureComponent: _n,
    memo: Hs,
    forwardRef: yr,
    flushSync: aa,
    unstable_batchedUpdates: sa,
    StrictMode: Ye,
    Suspense: xn,
    SuspenseList: Jt,
    lazy: Gs,
    __SECRET_INTERNALS_DO_NOT_USE_OR_YOU_WILL_BE_FIRED: ta,
  };
  var od = Object.freeze({
    __proto__: null,
    [Symbol.toStringTag]: 'Module',
    default: L,
    version: id,
    Children: Us,
    render: Ys,
    hydrate: Zs,
    unmountComponentAtNode: ra,
    createPortal: wr,
    createFactory: na,
    cloneElement: ia,
    isValidElement: br,
    findDOMNode: oa,
    PureComponent: _n,
    memo: Hs,
    forwardRef: yr,
    flushSync: aa,
    unstable_batchedUpdates: sa,
    StrictMode: rd,
    Suspense: xn,
    SuspenseList: Jt,
    lazy: Gs,
    __SECRET_INTERNALS_DO_NOT_USE_OR_YOU_WILL_BE_FIRED: ta,
    createElement: Ae,
    createContext: Yt,
    createRef: Xn,
    Fragment: Ye,
    Component: Oe,
    useState: yt,
    useReducer: fr,
    useEffect: kt,
    useLayoutEffect: pr,
    useRef: ut,
    useImperativeHandle: Ls,
    useMemo: wt,
    useCallback: ct,
    useContext: En,
    useDebugValue: ks,
    useErrorBoundary: Gh,
  });
  var xt =
    typeof globalThis != 'undefined'
      ? globalThis
      : typeof window != 'undefined'
      ? window
      : typeof global != 'undefined'
      ? global
      : typeof self != 'undefined'
      ? self
      : {};
  var Sr = { exports: {} };
  Er.prototype = {
    on: function (i, t, e) {
      var n = this.e || (this.e = {});
      (n[i] || (n[i] = [])).push({ fn: t, ctx: e });
      return this;
    },
    once: function (i, t, e) {
      function r() {
        n.off(i, r);
        t.apply(e, arguments);
      }
      var n = this;
      r._ = t;
      return this.on(i, r, e);
    },
    emit: function (i) {
      var t = [].slice.call(arguments, 1);
      var e = ((this.e || (this.e = {}))[i] || []).slice();
      var n = 0;
      var r = e.length;
      for (n; n < r; n++) {
        e[n].fn.apply(e[n].ctx, t);
      }
      return this;
    },
    off: function (i, t) {
      var e = this.e || (this.e = {});
      var n = e[i];
      var r = [];
      if (n && t) {
        var o = 0;
        for (var s = n.length; o < s; o++) {
          if (n[o].fn !== t && n[o].fn._ !== t) {
            r.push(n[o]);
          }
        }
      }
      if (r.length) {
        e[i] = r;
      } else {
        delete e[i];
      }
      return this;
    },
  };
  Sr.exports = Er;
  Sr.exports.TinyEmitter = Er;
  var Qn = Sr.exports;
  var $n = { exports: {} };
  var la = {};
  var Qt = Jn(od);
  var Tn = { exports: {} };
  var ad = 'SECRET_DO_NOT_PASS_THIS_OR_YOU_WILL_BE_FIRED';
  var ld = ad;
  var ud = ld;
  ca.resetWarningCache = ua;
  var cd = function () {
    function i(n, r, o, s, a, l) {
      if (l !== ud) {
        var u = new Error(
          'Calling PropTypes validators directly is not supported by the `prop-types` package. Use PropTypes.checkPropTypes() to call them. Read more at http://fb.me/use-check-prop-types'
        );
        u.name = 'Invariant Violation';
        throw u;
      }
    }
    function t() {
      return i;
    }
    i.isRequired = i;
    var e = {
      array: i,
      bigint: i,
      bool: i,
      func: i,
      number: i,
      object: i,
      string: i,
      symbol: i,
      any: i,
      arrayOf: t,
      element: i,
      elementType: i,
      instanceOf: t,
      node: i,
      objectOf: t,
      oneOf: t,
      oneOfType: t,
      shape: t,
      exact: t,
      checkPropTypes: ca,
      resetWarningCache: ua,
    };
    e.PropTypes = e;
    return e;
  };
  Tn.exports = cd();
  var dd = Object.freeze({
    __proto__: null,
    [Symbol.toStringTag]: 'Module',
    default: hd,
  });
  var fd = Jn(dd);
  var Te = {};
  var ht = {};
  Object.defineProperty(ht, '__esModule', { value: true });
  ht.findInArray = pd;
  ht.isFunction = md;
  ht.isNum = gd;
  ht.int = vd;
  ht.dontSetMe = yd;
  var Bt = {};
  Object.defineProperty(Bt, '__esModule', { value: true });
  Bt.getPrefix = da;
  Bt.browserPrefixToKey = fa;
  Bt.browserPrefixToStyle = wd;
  Bt.default = void 0;
  var _r = ['Moz', 'Webkit', 'O', 'ms'];
  var Sd = da();
  Bt.default = Sd;
  Object.defineProperty(Te, '__esModule', { value: true });
  Te.matchesSelector = wa;
  Te.matchesSelectorAndParentsTo = _d;
  Te.addEvent = xd;
  Te.removeEvent = Td;
  Te.outerHeight = Cd;
  Te.outerWidth = Pd;
  Te.innerHeight = Ad;
  Te.innerWidth = Od;
  Te.offsetXYFromParent = Dd;
  Te.createCSSTransform = Rd;
  Te.createSVGTransform = Md;
  Te.getTranslation = xr;
  Te.getTouch = Fd;
  Te.getTouchIdentifier = Ld;
  Te.addUserSelectStyles = kd;
  Te.removeUserSelectStyles = Bd;
  Te.addClassName = ba;
  Te.removeClassName = Sa;
  var Ze = ht;
  var pa = Ed(Bt);
  var ti = '';
  var dt = {};
  Object.defineProperty(dt, '__esModule', { value: true });
  dt.getBoundPosition = Nd;
  dt.snapToGrid = Id;
  dt.canDragX = Hd;
  dt.canDragY = zd;
  dt.getControlPosition = Vd;
  dt.createCoreData = Ud;
  dt.createDraggableData = Wd;
  var Ke = ht;
  var $t = Te;
  var ni = {};
  var ii = {};
  Object.defineProperty(ii, '__esModule', { value: true });
  ii.default = Gd;
  Object.defineProperty(ni, '__esModule', { value: true });
  ni.default = void 0;
  var Cr = Xd(Qt);
  var Je = Ar(Tn.exports);
  var qd = Ar(Qt);
  var ze = Te;
  var Nt = dt;
  var Pr = ht;
  var Pn = Ar(ii);
  var at = {
    touch: { start: 'touchstart', move: 'touchmove', stop: 'touchend' },
    mouse: { start: 'mousedown', move: 'mousemove', stop: 'mouseup' },
  };
  var Tt = at.mouse;
  var oi = (function () {
    function e() {
      $d(this, e);
      var r = arguments.length;
      var o = new Array(r);
      for (var s = 0; s < r; s++) {
        o[s] = arguments[s];
      }
      var n = t.call.apply(t, [this].concat(o));
      st(Ve(n), 'state', {
        dragging: false,
        lastX: NaN,
        lastY: NaN,
        touchIdentifier: null,
      });
      st(Ve(n), 'mounted', false);
      st(Ve(n), 'handleDragStart', function (a) {
        n.props.onMouseDown(a);
        if (
          !n.props.allowAnyClick &&
          typeof a.button == 'number' &&
          a.button !== 0
        ) {
          return false;
        }
        var l = n.findDOMNode();
        if (!l || !l.ownerDocument || !l.ownerDocument.body) {
          throw new Error('<DraggableCore> not mounted on DragStart!');
        }
        var u = l.ownerDocument;
        if (
          !n.props.disabled &&
          !!(a.target instanceof u.defaultView.Node) &&
          (!n.props.handle ||
            !!ze.matchesSelectorAndParentsTo(a.target, n.props.handle, l)) &&
          (!n.props.cancel ||
            !ze.matchesSelectorAndParentsTo(a.target, n.props.cancel, l))
        ) {
          if (a.type === 'touchstart') {
            a.preventDefault();
          }
          var c = ze.getTouchIdentifier(a);
          n.setState({ touchIdentifier: c });
          var h = Nt.getControlPosition(a, c, Ve(n));
          if (h != null) {
            var d = h.x;
            var g = h.y;
            var y = Nt.createCoreData(Ve(n), d, g);
            Pn.default('DraggableCore: handleDragStart: %j', y);
            Pn.default('calling', n.props.onStart);
            var x = n.props.onStart(a, y);
            if (x !== false && n.mounted !== false) {
              if (n.props.enableUserSelectHack) {
                ze.addUserSelectStyles(u);
              }
              n.setState({ dragging: true, lastX: d, lastY: g });
              ze.addEvent(u, Tt.move, n.handleDrag);
              ze.addEvent(u, Tt.stop, n.handleDragStop);
            }
          }
        }
      });
      st(Ve(n), 'handleDrag', function (a) {
        var l = Nt.getControlPosition(a, n.state.touchIdentifier, Ve(n));
        if (l != null) {
          var u = l.x;
          var c = l.y;
          if (Array.isArray(n.props.grid)) {
            var h = u - n.state.lastX;
            var d = c - n.state.lastY;
            var g = Nt.snapToGrid(n.props.grid, h, d);
            var y = Yd(g, 2);
            h = y[0];
            d = y[1];
            if (!h && !d) {
              return;
            }
            u = n.state.lastX + h;
            c = n.state.lastY + d;
          }
          var x = Nt.createCoreData(Ve(n), u, c);
          Pn.default('DraggableCore: handleDrag: %j', x);
          var b = n.props.onDrag(a, x);
          if (b === false || n.mounted === false) {
            try {
              n.handleDragStop(new MouseEvent('mouseup'));
            } catch {
              var T = document.createEvent('MouseEvents');
              T.initMouseEvent(
                'mouseup',
                true,
                true,
                window,
                0,
                0,
                0,
                0,
                0,
                false,
                false,
                false,
                false,
                0,
                null
              );
              n.handleDragStop(T);
            }
            return;
          }
          n.setState({ lastX: u, lastY: c });
        }
      });
      st(Ve(n), 'handleDragStop', function (a) {
        if (n.state.dragging) {
          var l = Nt.getControlPosition(a, n.state.touchIdentifier, Ve(n));
          if (l != null) {
            var u = l.x;
            var c = l.y;
            var h = Nt.createCoreData(Ve(n), u, c);
            var d = n.props.onStop(a, h);
            if (d === false || n.mounted === false) {
              return false;
            }
            var g = n.findDOMNode();
            if (g && n.props.enableUserSelectHack) {
              ze.removeUserSelectStyles(g.ownerDocument);
            }
            Pn.default('DraggableCore: handleDragStop: %j', h);
            n.setState({ dragging: false, lastX: NaN, lastY: NaN });
            if (g) {
              Pn.default('DraggableCore: Removing handlers');
              ze.removeEvent(g.ownerDocument, Tt.move, n.handleDrag);
              ze.removeEvent(g.ownerDocument, Tt.stop, n.handleDragStop);
            }
          }
        }
      });
      st(Ve(n), 'onMouseDown', function (a) {
        Tt = at.mouse;
        return n.handleDragStart(a);
      });
      st(Ve(n), 'onMouseUp', function (a) {
        Tt = at.mouse;
        return n.handleDragStop(a);
      });
      st(Ve(n), 'onTouchStart', function (a) {
        Tt = at.touch;
        return n.handleDragStart(a);
      });
      st(Ve(n), 'onTouchEnd', function (a) {
        Tt = at.touch;
        return n.handleDragStop(a);
      });
      return n;
    }
    var i = Cr.Component;
    tf(e, i);
    var t = nf(e);
    ef(e, [
      {
        key: 'componentDidMount',
        value: function () {
          this.mounted = true;
          var r = this.findDOMNode();
          if (r) {
            ze.addEvent(r, at.touch.start, this.onTouchStart, {
              passive: false,
            });
          }
        },
      },
      {
        key: 'componentWillUnmount',
        value: function () {
          this.mounted = false;
          var r = this.findDOMNode();
          if (r) {
            var o = r.ownerDocument;
            ze.removeEvent(o, at.mouse.move, this.handleDrag);
            ze.removeEvent(o, at.touch.move, this.handleDrag);
            ze.removeEvent(o, at.mouse.stop, this.handleDragStop);
            ze.removeEvent(o, at.touch.stop, this.handleDragStop);
            ze.removeEvent(r, at.touch.start, this.onTouchStart, {
              passive: false,
            });
            if (this.props.enableUserSelectHack) {
              ze.removeUserSelectStyles(o);
            }
          }
        },
      },
      {
        key: 'findDOMNode',
        value: function () {
          var r;
          var o;
          var s;
          if (
            (r =
              (o = this.props) === null ||
              o === void 0 ||
              (s = o.nodeRef) === null ||
              s === void 0
                ? void 0
                : s.current) !== null &&
            r !== void 0
          ) {
            return r;
          } else {
            return qd.default.findDOMNode(this);
          }
        },
      },
      {
        key: 'render',
        value: function () {
          return Cr.cloneElement(Cr.Children.only(this.props.children), {
            onMouseDown: this.onMouseDown,
            onMouseUp: this.onMouseUp,
            onTouchEnd: this.onTouchEnd,
          });
        },
      },
    ]);
    return e;
  })();
  ni.default = oi;
  st(oi, 'displayName', 'DraggableCore');
  st(oi, 'propTypes', {
    allowAnyClick: Je.default.bool,
    disabled: Je.default.bool,
    enableUserSelectHack: Je.default.bool,
    offsetParent: function (t, e) {
      if (t[e] && t[e].nodeType !== 1) {
        throw new Error("Draggable's offsetParent must be a DOM Node.");
      }
    },
    grid: Je.default.arrayOf(Je.default.number),
    handle: Je.default.string,
    cancel: Je.default.string,
    nodeRef: Je.default.object,
    onStart: Je.default.func,
    onDrag: Je.default.func,
    onStop: Je.default.func,
    onMouseDown: Je.default.func,
    scale: Je.default.number,
    className: Pr.dontSetMe,
    style: Pr.dontSetMe,
    transform: Pr.dontSetMe,
  });
  st(oi, 'defaultProps', {
    allowAnyClick: false,
    disabled: false,
    enableUserSelectHack: true,
    onStart: function () {},
    onDrag: function () {},
    onStop: function () {},
    onMouseDown: function () {},
    scale: 1,
  });
  (function () {
    function t(F) {
      if (typeof Symbol == 'function' && typeof Symbol.iterator == 'symbol') {
        t = function (z) {
          return typeof z;
        };
      } else {
        t = function (z) {
          if (
            z &&
            typeof Symbol == 'function' &&
            z.constructor === Symbol &&
            z !== Symbol.prototype
          ) {
            return 'symbol';
          } else {
            return typeof z;
          }
        };
      }
      return t(F);
    }
    function d(F) {
      if (F && F.__esModule) {
        return F;
      } else {
        return { default: F };
      }
    }
    function g(F) {
      if (typeof WeakMap != 'function') {
        return null;
      }
      var V = new WeakMap();
      var z = new WeakMap();
      return (g = function (G) {
        if (G) {
          return z;
        } else {
          return V;
        }
      })(F);
    }
    function y(F, V) {
      if (!V && F && F.__esModule) {
        return F;
      }
      if (F === null || (t(F) !== 'object' && typeof F != 'function')) {
        return { default: F };
      }
      var z = g(V);
      if (z && z.has(F)) {
        return z.get(F);
      }
      var j = {};
      var G = Object.defineProperty && Object.getOwnPropertyDescriptor;
      for (var $ in F) {
        if ($ !== 'default' && Object.prototype.hasOwnProperty.call(F, $)) {
          var ae = G ? Object.getOwnPropertyDescriptor(F, $) : null;
          if (ae && (ae.get || ae.set)) {
            Object.defineProperty(j, $, ae);
          } else {
            j[$] = F[$];
          }
        }
      }
      j.default = F;
      if (z) {
        z.set(F, j);
      }
      return j;
    }
    function x() {
      x =
        Object.assign ||
        function (F) {
          for (var V = 1; V < arguments.length; V++) {
            var z = arguments[V];
            for (var j in z) {
              if (Object.prototype.hasOwnProperty.call(z, j)) {
                F[j] = z[j];
              }
            }
          }
          return F;
        };
      return x.apply(this, arguments);
    }
    function b(F, V) {
      if (F == null) {
        return {};
      }
      var z = T(F, V);
      var j;
      var G;
      if (Object.getOwnPropertySymbols) {
        var $ = Object.getOwnPropertySymbols(F);
        for (G = 0; G < $.length; G++) {
          j = $[G];
          if (!(V.indexOf(j) >= 0)) {
            if (Object.prototype.propertyIsEnumerable.call(F, j)) {
              z[j] = F[j];
            }
          }
        }
      }
      return z;
    }
    function T(F, V) {
      if (F == null) {
        return {};
      }
      var z = {};
      var j = Object.keys(F);
      var G;
      for (var $ = 0; $ < j.length; $++) {
        G = j[$];
        if (!(V.indexOf(G) >= 0)) {
          z[G] = F[G];
        }
      }
      return z;
    }
    function f(F, V) {
      var z = Object.keys(F);
      if (Object.getOwnPropertySymbols) {
        var j = Object.getOwnPropertySymbols(F);
        if (V) {
          j = j.filter(function (G) {
            return Object.getOwnPropertyDescriptor(F, G).enumerable;
          });
        }
        z.push.apply(z, j);
      }
      return z;
    }
    function E(F) {
      for (var V = 1; V < arguments.length; V++) {
        var z = arguments[V] != null ? arguments[V] : {};
        if (V % 2) {
          f(Object(z), true).forEach(function (j) {
            q(F, j, z[j]);
          });
        } else if (Object.getOwnPropertyDescriptors) {
          Object.defineProperties(F, Object.getOwnPropertyDescriptors(z));
        } else {
          f(Object(z)).forEach(function (j) {
            Object.defineProperty(F, j, Object.getOwnPropertyDescriptor(z, j));
          });
        }
      }
      return F;
    }
    function A(F, V) {
      return B(F) || N(F, V) || O(F, V) || C();
    }
    function C() {
      throw new TypeError(`Invalid attempt to destructure non-iterable instance.
In order to be iterable, non-array objects must have a [Symbol.iterator]() method.`);
    }
    function O(F, V) {
      if (F) {
        if (typeof F == 'string') {
          return D(F, V);
        }
        var z = Object.prototype.toString.call(F).slice(8, -1);
        if (z === 'Object' && F.constructor) {
          z = F.constructor.name;
        }
        if (z === 'Map' || z === 'Set') {
          return Array.from(F);
        }
        if (
          z === 'Arguments' ||
          /^(?:Ui|I)nt(?:8|16|32)(?:Clamped)?Array$/.test(z)
        ) {
          return D(F, V);
        }
      }
    }
    function D(F, V) {
      if (V == null || V > F.length) {
        V = F.length;
      }
      var z = 0;
      for (var j = new Array(V); z < V; z++) {
        j[z] = F[z];
      }
      return j;
    }
    function N(F, V) {
      var z =
        F == null
          ? null
          : (typeof Symbol != 'undefined' && F[Symbol.iterator]) ||
            F['@@iterator'];
      if (z != null) {
        var j = [];
        var G = true;
        var $ = false;
        var ae;
        var Se;
        try {
          for (
            z = z.call(F);
            !(G = (ae = z.next()).done) &&
            (j.push(ae.value), !V || j.length !== V);
            G = true
          ) {}
        } catch (ge) {
          $ = true;
          Se = ge;
        } finally {
          try {
            if (!G && z.return != null) {
              z.return();
            }
          } finally {
            if ($) {
              throw Se;
            }
          }
        }
        return j;
      }
    }
    function B(F) {
      if (Array.isArray(F)) {
        return F;
      }
    }
    function Z(F, V) {
      if (!(F instanceof V)) {
        throw new TypeError('Cannot call a class as a function');
      }
    }
    function Y(F, V) {
      for (var z = 0; z < V.length; z++) {
        var j = V[z];
        j.enumerable = j.enumerable || false;
        j.configurable = true;
        if ('value' in j) {
          j.writable = true;
        }
        Object.defineProperty(F, j.key, j);
      }
    }
    function U(F, V, z) {
      if (V) {
        Y(F.prototype, V);
      }
      if (z) {
        Y(F, z);
      }
      return F;
    }
    function K(F, V) {
      if (typeof V != 'function' && V !== null) {
        throw new TypeError(
          'Super expression must either be null or a function'
        );
      }
      F.prototype = Object.create(V && V.prototype, {
        constructor: { value: F, writable: true, configurable: true },
      });
      if (V) {
        Q(F, V);
      }
    }
    function Q(F, V) {
      Q =
        Object.setPrototypeOf ||
        function (j, G) {
          j.__proto__ = G;
          return j;
        };
      return Q(F, V);
    }
    function le(F) {
      var V = fe();
      return function () {
        var j = me(F);
        var G;
        if (V) {
          var $ = me(this).constructor;
          G = Reflect.construct(j, arguments, $);
        } else {
          G = j.apply(this, arguments);
        }
        return re(this, G);
      };
    }
    function re(F, V) {
      if (V && (t(V) === 'object' || typeof V == 'function')) {
        return V;
      }
      if (V !== void 0) {
        throw new TypeError(
          'Derived constructors may only return object or undefined'
        );
      }
      return se(F);
    }
    function se(F) {
      if (F === void 0) {
        throw new ReferenceError(
          "this hasn't been initialised - super() hasn't been called"
        );
      }
      return F;
    }
    function fe() {
      if (
        typeof Reflect == 'undefined' ||
        !Reflect.construct ||
        Reflect.construct.sham
      ) {
        return false;
      }
      if (typeof Proxy == 'function') {
        return true;
      }
      try {
        Boolean.prototype.valueOf.call(
          Reflect.construct(Boolean, [], function () {})
        );
        return true;
      } catch {
        return false;
      }
    }
    function me(F) {
      me = Object.setPrototypeOf
        ? Object.getPrototypeOf
        : function (z) {
            return z.__proto__ || Object.getPrototypeOf(z);
          };
      return me(F);
    }
    function q(F, V, z) {
      if (V in F) {
        Object.defineProperty(F, V, {
          value: z,
          enumerable: true,
          configurable: true,
          writable: true,
        });
      } else {
        F[V] = z;
      }
      return F;
    }
    var i = la;
    Object.defineProperty(i, '__esModule', { value: true });
    Object.defineProperty(i, 'DraggableCore', {
      enumerable: true,
      get: function () {
        return u.default;
      },
    });
    i.default = void 0;
    var e = y(Qt);
    var n = d(Tn.exports);
    var r = d(Qt);
    var o = d(fd);
    var s = Te;
    var a = dt;
    var l = ht;
    var u = d(ni);
    var c = d(ii);
    var h = [
      'axis',
      'bounds',
      'children',
      'defaultPosition',
      'defaultClassName',
      'defaultClassNameDragging',
      'defaultClassNameDragged',
      'position',
      'positionOffset',
      'scale',
    ];
    var Le = (function () {
      function z(j) {
        Z(this, z);
        var G = V.call(this, j);
        q(se(G), 'onDragStart', function ($, ae) {
          c.default('Draggable: onDragStart: %j', ae);
          var Se = G.props.onStart($, a.createDraggableData(se(G), ae));
          if (Se === false) {
            return false;
          }
          G.setState({ dragging: true, dragged: true });
        });
        q(se(G), 'onDrag', function ($, ae) {
          if (!G.state.dragging) {
            return false;
          }
          c.default('Draggable: onDrag: %j', ae);
          var Se = a.createDraggableData(se(G), ae);
          var ge = { x: Se.x, y: Se.y };
          if (G.props.bounds) {
            var tt = ge.x;
            var nt = ge.y;
            ge.x += G.state.slackX;
            ge.y += G.state.slackY;
            var it = a.getBoundPosition(se(G), ge.x, ge.y);
            var p = A(it, 2);
            var _ = p[0];
            var R = p[1];
            ge.x = _;
            ge.y = R;
            ge.slackX = G.state.slackX + (tt - ge.x);
            ge.slackY = G.state.slackY + (nt - ge.y);
            Se.x = ge.x;
            Se.y = ge.y;
            Se.deltaX = ge.x - G.state.x;
            Se.deltaY = ge.y - G.state.y;
          }
          var I = G.props.onDrag($, Se);
          if (I === false) {
            return false;
          }
          G.setState(ge);
        });
        q(se(G), 'onDragStop', function ($, ae) {
          if (!G.state.dragging) {
            return false;
          }
          var Se = G.props.onStop($, a.createDraggableData(se(G), ae));
          if (Se === false) {
            return false;
          }
          c.default('Draggable: onDragStop: %j', ae);
          var ge = { dragging: false, slackX: 0, slackY: 0 };
          var tt = Boolean(G.props.position);
          if (tt) {
            var nt = G.props.position;
            var it = nt.x;
            var p = nt.y;
            ge.x = it;
            ge.y = p;
          }
          G.setState(ge);
        });
        G.state = {
          dragging: false,
          dragged: false,
          x: j.position ? j.position.x : j.defaultPosition.x,
          y: j.position ? j.position.y : j.defaultPosition.y,
          prevPropsPosition: E({}, j.position),
          slackX: 0,
          slackY: 0,
          isElementSVG: false,
        };
        if (j.position && !j.onDrag && !j.onStop) {
          console.warn(
            'A `position` was applied to this <Draggable>, without drag handlers. This will make this component effectively undraggable. Please attach `onDrag` or `onStop` handlers so you can adjust the `position` of this element.'
          );
        }
        return G;
      }
      var F = e.Component;
      K(z, F);
      var V = le(z);
      U(
        z,
        [
          {
            key: 'componentDidMount',
            value: function () {
              if (
                typeof window.SVGElement != 'undefined' &&
                this.findDOMNode() instanceof window.SVGElement
              ) {
                this.setState({ isElementSVG: true });
              }
            },
          },
          {
            key: 'componentWillUnmount',
            value: function () {
              this.setState({ dragging: false });
            },
          },
          {
            key: 'findDOMNode',
            value: function () {
              var G;
              var $;
              var ae;
              if (
                (G =
                  ($ = this.props) === null ||
                  $ === void 0 ||
                  (ae = $.nodeRef) === null ||
                  ae === void 0
                    ? void 0
                    : ae.current) !== null &&
                G !== void 0
              ) {
                return G;
              } else {
                return r.default.findDOMNode(this);
              }
            },
          },
          {
            key: 'render',
            value: function () {
              var G;
              var $ = this.props;
              $.axis;
              $.bounds;
              var ae = $.children;
              var Se = $.defaultPosition;
              var ge = $.defaultClassName;
              var tt = $.defaultClassNameDragging;
              var nt = $.defaultClassNameDragged;
              var it = $.position;
              var p = $.positionOffset;
              $.scale;
              var _ = b($, h);
              var R = {};
              var I = null;
              var k = Boolean(it);
              var J = !k || this.state.dragging;
              var X = it || Se;
              var pe = {
                x: a.canDragX(this) && J ? this.state.x : X.x,
                y: a.canDragY(this) && J ? this.state.y : X.y,
              };
              if (this.state.isElementSVG) {
                I = s.createSVGTransform(pe, p);
              } else {
                R = s.createCSSTransform(pe, p);
              }
              var _e = o.default(
                ae.props.className || '',
                ge,
                ((G = {}),
                q(G, tt, this.state.dragging),
                q(G, nt, this.state.dragged),
                G)
              );
              return e.createElement(
                u.default,
                x({}, _, {
                  onStart: this.onDragStart,
                  onDrag: this.onDrag,
                  onStop: this.onDragStop,
                }),
                e.cloneElement(e.Children.only(ae), {
                  className: _e,
                  style: E(E({}, ae.props.style), R),
                  transform: I,
                })
              );
            },
          },
        ],
        [
          {
            key: 'getDerivedStateFromProps',
            value: function (G, $) {
              var ae = G.position;
              var Se = $.prevPropsPosition;
              if (ae && (!Se || ae.x !== Se.x || ae.y !== Se.y)) {
                c.default('Draggable: getDerivedStateFromProps %j', {
                  position: ae,
                  prevPropsPosition: Se,
                });
                return { x: ae.x, y: ae.y, prevPropsPosition: E({}, ae) };
              } else {
                return null;
              }
            },
          },
        ]
      );
      return z;
    })();
    i.default = Le;
    q(Le, 'displayName', 'Draggable');
    q(
      Le,
      'propTypes',
      E(
        E({}, u.default.propTypes),
        {},
        {
          axis: n.default.oneOf(['both', 'x', 'y', 'none']),
          bounds: n.default.oneOfType([
            n.default.shape({
              left: n.default.number,
              right: n.default.number,
              top: n.default.number,
              bottom: n.default.number,
            }),
            n.default.string,
            n.default.oneOf([false]),
          ]),
          defaultClassName: n.default.string,
          defaultClassNameDragging: n.default.string,
          defaultClassNameDragged: n.default.string,
          defaultPosition: n.default.shape({
            x: n.default.number,
            y: n.default.number,
          }),
          positionOffset: n.default.shape({
            x: n.default.oneOfType([n.default.number, n.default.string]),
            y: n.default.oneOfType([n.default.number, n.default.string]),
          }),
          position: n.default.shape({
            x: n.default.number,
            y: n.default.number,
          }),
          className: l.dontSetMe,
          style: l.dontSetMe,
          transform: l.dontSetMe,
        }
      )
    );
    q(
      Le,
      'defaultProps',
      E(
        E({}, u.default.defaultProps),
        {},
        {
          axis: 'both',
          bounds: false,
          defaultClassName: 'react-draggable',
          defaultClassNameDragging: 'react-draggable-dragging',
          defaultClassNameDragged: 'react-draggable-dragged',
          defaultPosition: { x: 0, y: 0 },
          scale: 1,
        }
      )
    );
  })();
  var Ta = la;
  var Ca = Ta.default;
  var sf = Ta.DraggableCore;
  $n.exports = Ca;
  $n.exports.default = Ca;
  $n.exports.DraggableCore = sf;
  var af = $n.exports;
  var lf = ['second', 'minute', 'hour', 'day', 'week', 'month', 'year'];
  var cf = [
    '\u79D2',
    '\u5206\u949F',
    '\u5C0F\u65F6',
    '\u5929',
    '\u5468',
    '\u4E2A\u6708',
    '\u5E74',
  ];
  var Dr = {};
  var Re = function (i, t) {
    Dr[i] = t;
  };
  var Pa = function (i) {
    return Dr[i] || Dr.en_US;
  };
  var It = [60, 60, 24, 7, 365 / 7 / 12, 12];
  var ff = function (i, t, e) {
    var n = Da(i, e && e.relativeDate);
    return Oa(n, Pa(t));
  };
  var Ra = 'timeago-id';
  var Rr = {};
  var Mr = function (i) {
    clearTimeout(i);
    delete Rr[i];
  };
  Re('en_US', uf);
  Re('zh_CN', hf);
  var vf =
    (globalThis && globalThis.__extends) ||
    (function () {
      var i = function (t, e) {
        i =
          Object.setPrototypeOf ||
          ({ __proto__: [] } instanceof Array &&
            function (n, r) {
              n.__proto__ = r;
            }) ||
          function (n, r) {
            for (var o in r) {
              if (r.hasOwnProperty(o)) {
                n[o] = r[o];
              }
            }
          };
        return i(t, e);
      };
      return function (t, e) {
        function n() {
          this.constructor = t;
        }
        i(t, e);
        t.prototype =
          e === null
            ? Object.create(e)
            : ((n.prototype = e.prototype), new n());
      };
    })();
  var Fr =
    (globalThis && globalThis.__assign) ||
    function () {
      Fr =
        Object.assign ||
        function (i) {
          var t;
          var e = 1;
          for (var n = arguments.length; e < n; e++) {
            t = arguments[e];
            for (var r in t) {
              if (Object.prototype.hasOwnProperty.call(t, r)) {
                i[r] = t[r];
              }
            }
          }
          return i;
        };
      return Fr.apply(this, arguments);
    };
  var yf =
    (globalThis && globalThis.__rest) ||
    function (i, t) {
      var e = {};
      for (var n in i) {
        if (Object.prototype.hasOwnProperty.call(i, n) && t.indexOf(n) < 0) {
          e[n] = i[n];
        }
      }
      if (i != null && typeof Object.getOwnPropertySymbols == 'function') {
        var r = 0;
        for (var n = Object.getOwnPropertySymbols(i); r < n.length; r++) {
          if (
            t.indexOf(n[r]) < 0 &&
            Object.prototype.propertyIsEnumerable.call(i, n[r])
          ) {
            e[n[r]] = i[n[r]];
          }
        }
      }
      return e;
    };
  var wf = function (i) {
    return '' + (i instanceof Date ? i.getTime() : i);
  };
  var bf = (function () {
    function t() {
      var e = (i !== null && i.apply(this, arguments)) || this;
      e.dom = null;
      return e;
    }
    var i = _n;
    vf(t, i);
    t.prototype.componentDidMount = function () {
      this.renderTimeAgo();
    };
    t.prototype.componentDidUpdate = function () {
      this.renderTimeAgo();
    };
    t.prototype.renderTimeAgo = function () {
      var e = this.props;
      var n = e.live;
      var r = e.datetime;
      var o = e.locale;
      var s = e.opts;
      La(this.dom);
      if (n !== false) {
        this.dom.setAttribute('datetime', wf(r));
        gf(this.dom, o, s);
      }
    };
    t.prototype.componentWillUnmount = function () {
      La(this.dom);
    };
    t.prototype.render = function () {
      var e = this;
      var n = this.props;
      var r = n.datetime;
      n.live;
      var o = n.locale;
      var s = n.opts;
      var a = yf(n, ['datetime', 'live', 'locale', 'opts']);
      return Ae(
        'time',
        Fr(
          {
            ref: function (l) {
              e.dom = l;
            },
          },
          a
        ),
        ff(r, o, s)
      );
    };
    t.defaultProps = { live: true, className: '' };
    return t;
  })();
  var ka = Object.prototype.toString;
  var Ba = function (t) {
    var e = ka.call(t);
    var n = e === '[object Arguments]';
    if (!n) {
      n =
        e !== '[object Array]' &&
        t !== null &&
        typeof t == 'object' &&
        typeof t.length == 'number' &&
        t.length >= 0 &&
        ka.call(t.callee) === '[object Function]';
    }
    return n;
  };
  var Na;
  if (!Object.keys) {
    var si = Object.prototype.hasOwnProperty;
    var Ia = Object.prototype.toString;
    var Ef = Ba;
    var Ha = Object.prototype.propertyIsEnumerable;
    var _f = !Ha.call({ toString: null }, 'toString');
    var xf = Ha.call(function () {}, 'prototype');
    var ai = [
      'toString',
      'toLocaleString',
      'valueOf',
      'hasOwnProperty',
      'isPrototypeOf',
      'propertyIsEnumerable',
      'constructor',
    ];
    var Lr = function (i) {
      var t = i.constructor;
      return t && t.prototype === i;
    };
    var Tf = {
      $applicationCache: true,
      $console: true,
      $external: true,
      $frame: true,
      $frameElement: true,
      $frames: true,
      $innerHeight: true,
      $innerWidth: true,
      $onmozfullscreenchange: true,
      $onmozfullscreenerror: true,
      $outerHeight: true,
      $outerWidth: true,
      $pageXOffset: true,
      $pageYOffset: true,
      $parent: true,
      $scrollLeft: true,
      $scrollTop: true,
      $scrollX: true,
      $scrollY: true,
      $self: true,
      $webkitIndexedDB: true,
      $webkitStorageInfo: true,
      $window: true,
    };
    var Cf = (function () {
      if (typeof window == 'undefined') {
        return false;
      }
      for (var i in window) {
        try {
          if (
            !Tf['$' + i] &&
            si.call(window, i) &&
            window[i] !== null &&
            typeof window[i] == 'object'
          ) {
            try {
              Lr(window[i]);
            } catch {
              return true;
            }
          }
        } catch {
          return true;
        }
      }
      return false;
    })();
    var Pf = function (i) {
      if (typeof window == 'undefined' || !Cf) {
        return Lr(i);
      }
      try {
        return Lr(i);
      } catch {
        return false;
      }
    };
    Na = function (t) {
      var e = t !== null && typeof t == 'object';
      var n = Ia.call(t) === '[object Function]';
      var r = Ef(t);
      var o = e && Ia.call(t) === '[object String]';
      var s = [];
      if (!e && !n && !r) {
        throw new TypeError('Object.keys called on a non-object');
      }
      var a = xf && n;
      if (o && t.length > 0 && !si.call(t, 0)) {
        for (var l = 0; l < t.length; ++l) {
          s.push(String(l));
        }
      }
      if (r && t.length > 0) {
        for (var u = 0; u < t.length; ++u) {
          s.push(String(u));
        }
      } else {
        for (var c in t) {
          if ((!a || c !== 'prototype') && si.call(t, c)) {
            s.push(String(c));
          }
        }
      }
      if (_f) {
        var h = Pf(t);
        for (var d = 0; d < ai.length; ++d) {
          if ((!h || ai[d] !== 'constructor') && si.call(t, ai[d])) {
            s.push(ai[d]);
          }
        }
      }
      return s;
    };
  }
  var Af = Na;
  var Of = Array.prototype.slice;
  var Df = Ba;
  var za = Object.keys;
  var li = za
    ? function (t) {
        return za(t);
      }
    : Af;
  var Va = Object.keys;
  li.shim = function () {
    if (Object.keys) {
      var t = (function () {
        var e = Object.keys(arguments);
        return e && e.length === arguments.length;
      })();
      if (!t) {
        Object.keys = function (n) {
          if (Df(n)) {
            return Va(Of.call(n));
          } else {
            return Va(n);
          }
        };
      }
    } else {
      Object.keys = li;
    }
    return Object.keys || li;
  };
  var Rf = li;
  var Mf = Rf;
  var Ff = typeof Symbol == 'function' && typeof Symbol('foo') == 'symbol';
  var Lf = Object.prototype.toString;
  var kf = Array.prototype.concat;
  var kr = Object.defineProperty;
  var Bf = function (i) {
    return typeof i == 'function' && Lf.call(i) === '[object Function]';
  };
  var Nf = function () {
    var i = {};
    try {
      kr(i, 'x', { enumerable: false, value: i });
      for (var t in i) {
        return false;
      }
      return i.x === i;
    } catch {
      return false;
    }
  };
  var Ua = kr && Nf();
  var If = function (i, t, e, n) {
    if (!(t in i) || (!!Bf(n) && !!n())) {
      if (Ua) {
        kr(i, t, {
          configurable: true,
          enumerable: false,
          value: e,
          writable: true,
        });
      } else {
        i[t] = e;
      }
    }
  };
  var Wa = function (i, t) {
    var e = arguments.length > 2 ? arguments[2] : {};
    var n = Mf(t);
    if (Ff) {
      n = kf.call(n, Object.getOwnPropertySymbols(t));
    }
    for (var r = 0; r < n.length; r += 1) {
      If(i, n[r], t[n[r]], e[n[r]]);
    }
  };
  Wa.supportsDescriptors = !!Ua;
  var en = Wa;
  var tn = { exports: {} };
  var Hf = 'Function.prototype.bind called on incompatible ';
  var Br = Array.prototype.slice;
  var zf = Object.prototype.toString;
  var Vf = '[object Function]';
  var Uf = function (t) {
    var e = this;
    if (typeof e != 'function' || zf.call(e) !== Vf) {
      throw new TypeError(Hf + e);
    }
    var n = Br.call(arguments, 1);
    var o = function () {
      if (this instanceof r) {
        var c = e.apply(this, n.concat(Br.call(arguments)));
        if (Object(c) === c) {
          return c;
        } else {
          return this;
        }
      } else {
        return e.apply(t, n.concat(Br.call(arguments)));
      }
    };
    var s = Math.max(0, e.length - n.length);
    var a = [];
    for (var l = 0; l < s; l++) {
      a.push('$' + l);
    }
    var r = Function(
      'binder',
      'return function (' +
        a.join(',') +
        '){ return binder.apply(this,arguments); }'
    )(o);
    if (e.prototype) {
      var u = function () {};
      u.prototype = e.prototype;
      r.prototype = new u();
      u.prototype = null;
    }
    return r;
  };
  var Wf = Uf;
  var Nr = Function.prototype.bind || Wf;
  var ja = function () {
    if (
      typeof Symbol != 'function' ||
      typeof Object.getOwnPropertySymbols != 'function'
    ) {
      return false;
    }
    if (typeof Symbol.iterator == 'symbol') {
      return true;
    }
    var t = {};
    var e = Symbol('test');
    var n = Object(e);
    if (
      typeof e == 'string' ||
      Object.prototype.toString.call(e) !== '[object Symbol]' ||
      Object.prototype.toString.call(n) !== '[object Symbol]'
    ) {
      return false;
    }
    var r = 42;
    t[e] = r;
    for (e in t) {
      return false;
    }
    if (
      (typeof Object.keys == 'function' && Object.keys(t).length !== 0) ||
      (typeof Object.getOwnPropertyNames == 'function' &&
        Object.getOwnPropertyNames(t).length !== 0)
    ) {
      return false;
    }
    var o = Object.getOwnPropertySymbols(t);
    if (
      o.length !== 1 ||
      o[0] !== e ||
      !Object.prototype.propertyIsEnumerable.call(t, e)
    ) {
      return false;
    }
    if (typeof Object.getOwnPropertyDescriptor == 'function') {
      var s = Object.getOwnPropertyDescriptor(t, e);
      if (s.value !== r || s.enumerable !== true) {
        return false;
      }
    }
    return true;
  };
  var Ga = typeof Symbol != 'undefined' && Symbol;
  var jf = ja;
  var qa = function () {
    if (
      typeof Ga != 'function' ||
      typeof Symbol != 'function' ||
      typeof Ga('foo') != 'symbol' ||
      typeof Symbol('bar') != 'symbol'
    ) {
      return false;
    } else {
      return jf();
    }
  };
  var Gf = Nr;
  var Xa = Gf.call(Function.call, Object.prototype.hasOwnProperty);
  var ce;
  var An = SyntaxError;
  var Ya = Function;
  var nn = TypeError;
  var Ir = function (i) {
    try {
      return Ya('"use strict"; return (' + i + ').constructor;')();
    } catch {}
  };
  var Ht = Object.getOwnPropertyDescriptor;
  if (Ht) {
    try {
      Ht({}, '');
    } catch {
      Ht = null;
    }
  }
  var Hr = function () {
    throw new nn();
  };
  var qf = Ht
    ? (function () {
        try {
          arguments.callee;
          return Hr;
        } catch {
          try {
            return Ht(arguments, 'callee').get;
          } catch {
            return Hr;
          }
        }
      })()
    : Hr;
  var rn = qa();
  var Ct =
    Object.getPrototypeOf ||
    function (i) {
      return i.__proto__;
    };
  var on = {};
  var Xf = typeof Uint8Array == 'undefined' ? ce : Ct(Uint8Array);
  var sn = {
    '%AggregateError%':
      typeof AggregateError == 'undefined' ? ce : AggregateError,
    '%Array%': Array,
    '%ArrayBuffer%': typeof ArrayBuffer == 'undefined' ? ce : ArrayBuffer,
    '%ArrayIteratorPrototype%': rn ? Ct([][Symbol.iterator]()) : ce,
    '%AsyncFromSyncIteratorPrototype%': ce,
    '%AsyncFunction%': on,
    '%AsyncGenerator%': on,
    '%AsyncGeneratorFunction%': on,
    '%AsyncIteratorPrototype%': on,
    '%Atomics%': typeof Atomics == 'undefined' ? ce : Atomics,
    '%BigInt%': typeof BigInt == 'undefined' ? ce : BigInt,
    '%Boolean%': Boolean,
    '%DataView%': typeof DataView == 'undefined' ? ce : DataView,
    '%Date%': Date,
    '%decodeURI%': decodeURI,
    '%decodeURIComponent%': decodeURIComponent,
    '%encodeURI%': encodeURI,
    '%encodeURIComponent%': encodeURIComponent,
    '%Error%': Error,
    '%eval%': eval,
    '%EvalError%': EvalError,
    '%Float32Array%': typeof Float32Array == 'undefined' ? ce : Float32Array,
    '%Float64Array%': typeof Float64Array == 'undefined' ? ce : Float64Array,
    '%FinalizationRegistry%':
      typeof FinalizationRegistry == 'undefined' ? ce : FinalizationRegistry,
    '%Function%': Ya,
    '%GeneratorFunction%': on,
    '%Int8Array%': typeof Int8Array == 'undefined' ? ce : Int8Array,
    '%Int16Array%': typeof Int16Array == 'undefined' ? ce : Int16Array,
    '%Int32Array%': typeof Int32Array == 'undefined' ? ce : Int32Array,
    '%isFinite%': isFinite,
    '%isNaN%': isNaN,
    '%IteratorPrototype%': rn ? Ct(Ct([][Symbol.iterator]())) : ce,
    '%JSON%': typeof JSON == 'object' ? JSON : ce,
    '%Map%': typeof Map == 'undefined' ? ce : Map,
    '%MapIteratorPrototype%':
      typeof Map == 'undefined' || !rn ? ce : Ct(new Map()[Symbol.iterator]()),
    '%Math%': Math,
    '%Number%': Number,
    '%Object%': Object,
    '%parseFloat%': parseFloat,
    '%parseInt%': parseInt,
    '%Promise%': typeof Promise == 'undefined' ? ce : Promise,
    '%Proxy%': typeof Proxy == 'undefined' ? ce : Proxy,
    '%RangeError%': RangeError,
    '%ReferenceError%': ReferenceError,
    '%Reflect%': typeof Reflect == 'undefined' ? ce : Reflect,
    '%RegExp%': RegExp,
    '%Set%': typeof Set == 'undefined' ? ce : Set,
    '%SetIteratorPrototype%':
      typeof Set == 'undefined' || !rn ? ce : Ct(new Set()[Symbol.iterator]()),
    '%SharedArrayBuffer%':
      typeof SharedArrayBuffer == 'undefined' ? ce : SharedArrayBuffer,
    '%String%': String,
    '%StringIteratorPrototype%': rn ? Ct(''[Symbol.iterator]()) : ce,
    '%Symbol%': rn ? Symbol : ce,
    '%SyntaxError%': An,
    '%ThrowTypeError%': qf,
    '%TypedArray%': Xf,
    '%TypeError%': nn,
    '%Uint8Array%': typeof Uint8Array == 'undefined' ? ce : Uint8Array,
    '%Uint8ClampedArray%':
      typeof Uint8ClampedArray == 'undefined' ? ce : Uint8ClampedArray,
    '%Uint16Array%': typeof Uint16Array == 'undefined' ? ce : Uint16Array,
    '%Uint32Array%': typeof Uint32Array == 'undefined' ? ce : Uint32Array,
    '%URIError%': URIError,
    '%WeakMap%': typeof WeakMap == 'undefined' ? ce : WeakMap,
    '%WeakRef%': typeof WeakRef == 'undefined' ? ce : WeakRef,
    '%WeakSet%': typeof WeakSet == 'undefined' ? ce : WeakSet,
  };
  var Yf = function i(t) {
    var e;
    if (t === '%AsyncFunction%') {
      e = Ir('async function () {}');
    } else if (t === '%GeneratorFunction%') {
      e = Ir('function* () {}');
    } else if (t === '%AsyncGeneratorFunction%') {
      e = Ir('async function* () {}');
    } else if (t === '%AsyncGenerator%') {
      var n = i('%AsyncGeneratorFunction%');
      if (n) {
        e = n.prototype;
      }
    } else if (t === '%AsyncIteratorPrototype%') {
      var r = i('%AsyncGenerator%');
      if (r) {
        e = Ct(r.prototype);
      }
    }
    sn[t] = e;
    return e;
  };
  var Za = {
    '%ArrayBufferPrototype%': ['ArrayBuffer', 'prototype'],
    '%ArrayPrototype%': ['Array', 'prototype'],
    '%ArrayProto_entries%': ['Array', 'prototype', 'entries'],
    '%ArrayProto_forEach%': ['Array', 'prototype', 'forEach'],
    '%ArrayProto_keys%': ['Array', 'prototype', 'keys'],
    '%ArrayProto_values%': ['Array', 'prototype', 'values'],
    '%AsyncFunctionPrototype%': ['AsyncFunction', 'prototype'],
    '%AsyncGenerator%': ['AsyncGeneratorFunction', 'prototype'],
    '%AsyncGeneratorPrototype%': [
      'AsyncGeneratorFunction',
      'prototype',
      'prototype',
    ],
    '%BooleanPrototype%': ['Boolean', 'prototype'],
    '%DataViewPrototype%': ['DataView', 'prototype'],
    '%DatePrototype%': ['Date', 'prototype'],
    '%ErrorPrototype%': ['Error', 'prototype'],
    '%EvalErrorPrototype%': ['EvalError', 'prototype'],
    '%Float32ArrayPrototype%': ['Float32Array', 'prototype'],
    '%Float64ArrayPrototype%': ['Float64Array', 'prototype'],
    '%FunctionPrototype%': ['Function', 'prototype'],
    '%Generator%': ['GeneratorFunction', 'prototype'],
    '%GeneratorPrototype%': ['GeneratorFunction', 'prototype', 'prototype'],
    '%Int8ArrayPrototype%': ['Int8Array', 'prototype'],
    '%Int16ArrayPrototype%': ['Int16Array', 'prototype'],
    '%Int32ArrayPrototype%': ['Int32Array', 'prototype'],
    '%JSONParse%': ['JSON', 'parse'],
    '%JSONStringify%': ['JSON', 'stringify'],
    '%MapPrototype%': ['Map', 'prototype'],
    '%NumberPrototype%': ['Number', 'prototype'],
    '%ObjectPrototype%': ['Object', 'prototype'],
    '%ObjProto_toString%': ['Object', 'prototype', 'toString'],
    '%ObjProto_valueOf%': ['Object', 'prototype', 'valueOf'],
    '%PromisePrototype%': ['Promise', 'prototype'],
    '%PromiseProto_then%': ['Promise', 'prototype', 'then'],
    '%Promise_all%': ['Promise', 'all'],
    '%Promise_reject%': ['Promise', 'reject'],
    '%Promise_resolve%': ['Promise', 'resolve'],
    '%RangeErrorPrototype%': ['RangeError', 'prototype'],
    '%ReferenceErrorPrototype%': ['ReferenceError', 'prototype'],
    '%RegExpPrototype%': ['RegExp', 'prototype'],
    '%SetPrototype%': ['Set', 'prototype'],
    '%SharedArrayBufferPrototype%': ['SharedArrayBuffer', 'prototype'],
    '%StringPrototype%': ['String', 'prototype'],
    '%SymbolPrototype%': ['Symbol', 'prototype'],
    '%SyntaxErrorPrototype%': ['SyntaxError', 'prototype'],
    '%TypedArrayPrototype%': ['TypedArray', 'prototype'],
    '%TypeErrorPrototype%': ['TypeError', 'prototype'],
    '%Uint8ArrayPrototype%': ['Uint8Array', 'prototype'],
    '%Uint8ClampedArrayPrototype%': ['Uint8ClampedArray', 'prototype'],
    '%Uint16ArrayPrototype%': ['Uint16Array', 'prototype'],
    '%Uint32ArrayPrototype%': ['Uint32Array', 'prototype'],
    '%URIErrorPrototype%': ['URIError', 'prototype'],
    '%WeakMapPrototype%': ['WeakMap', 'prototype'],
    '%WeakSetPrototype%': ['WeakSet', 'prototype'],
  };
  var ui = Nr;
  var ci = Xa;
  var Zf = ui.call(Function.call, Array.prototype.concat);
  var Kf = ui.call(Function.apply, Array.prototype.splice);
  var Ka = ui.call(Function.call, String.prototype.replace);
  var hi = ui.call(Function.call, String.prototype.slice);
  var Jf =
    /[^%.[\]]+|\[(?:(-?\d+(?:\.\d+)?)|(["'])((?:(?!\2)[^\\]|\\.)*?)\2)\]|(?=(?:\.|\[\])(?:\.|\[\]|%$))/g;
  var Qf = /\\(\\)?/g;
  var $f = function (t) {
    var e = hi(t, 0, 1);
    var n = hi(t, -1);
    if (e === '%' && n !== '%') {
      throw new An('invalid intrinsic syntax, expected closing `%`');
    }
    if (n === '%' && e !== '%') {
      throw new An('invalid intrinsic syntax, expected opening `%`');
    }
    var r = [];
    Ka(t, Jf, function (o, s, a, l) {
      r[r.length] = a ? Ka(l, Qf, '$1') : s || o;
    });
    return r;
  };
  var ep = function (t, e) {
    var n = t;
    var r;
    if (ci(Za, n)) {
      r = Za[n];
      n = '%' + r[0] + '%';
    }
    if (ci(sn, n)) {
      var o = sn[n];
      if (o === on) {
        o = Yf(n);
      }
      if (typeof o == 'undefined' && !e) {
        throw new nn(
          'intrinsic ' +
            t +
            ' exists, but is not available. Please file an issue!'
        );
      }
      return { alias: r, name: n, value: o };
    }
    throw new An('intrinsic ' + t + ' does not exist!');
  };
  var Ge = function (t, e) {
    if (typeof t != 'string' || t.length === 0) {
      throw new nn('intrinsic name must be a non-empty string');
    }
    if (arguments.length > 1 && typeof e != 'boolean') {
      throw new nn('"allowMissing" argument must be a boolean');
    }
    var n = $f(t);
    var r = n.length > 0 ? n[0] : '';
    var o = ep('%' + r + '%', e);
    var s = o.name;
    var a = o.value;
    var l = false;
    var u = o.alias;
    if (u) {
      r = u[0];
      Kf(n, Zf([0, 1], u));
    }
    var c = 1;
    for (var h = true; c < n.length; c += 1) {
      var d = n[c];
      var g = hi(d, 0, 1);
      var y = hi(d, -1);
      if (
        (g === '"' ||
          g === "'" ||
          g === '`' ||
          y === '"' ||
          y === "'" ||
          y === '`') &&
        g !== y
      ) {
        throw new An('property names with quotes must have matching quotes');
      }
      if (d === 'constructor' || !h) {
        l = true;
      }
      r += '.' + d;
      s = '%' + r + '%';
      if (ci(sn, s)) {
        a = sn[s];
      } else if (a != null) {
        if (!(d in a)) {
          if (!e) {
            throw new nn(
              'base intrinsic for ' +
                t +
                ' exists, but the property is not available.'
            );
          }
          return;
        }
        if (Ht && c + 1 >= n.length) {
          var x = Ht(a, d);
          h = !!x;
          if (h && 'get' in x && !('originalValue' in x.get)) {
            a = x.get;
          } else {
            a = a[d];
          }
        } else {
          h = ci(a, d);
          a = a[d];
        }
        if (h && !l) {
          sn[s] = a;
        }
      }
    }
    return a;
  };
  (function () {
    var i = tn;
    var t = Nr;
    var e = Ge;
    var n = e('%Function.prototype.apply%');
    var r = e('%Function.prototype.call%');
    var o = e('%Reflect.apply%', true) || t.call(r, n);
    var s = e('%Object.getOwnPropertyDescriptor%', true);
    var a = e('%Object.defineProperty%', true);
    var l = e('%Math.max%');
    if (a) {
      try {
        a({}, 'a', { value: 1 });
      } catch {
        a = null;
      }
    }
    i.exports = function (h) {
      var d = o(t, r, arguments);
      if (s && a) {
        var g = s(d, 'length');
        if (g.configurable) {
          a(d, 'length', {
            value: 1 + l(0, h.length - (arguments.length - 1)),
          });
        }
      }
      return d;
    };
    var u = function () {
      return o(t, n, arguments);
    };
    if (a) {
      a(i.exports, 'apply', { value: u });
    } else {
      i.exports.apply = u;
    }
  })();
  var Ja = Ge;
  var Qa = tn.exports;
  var tp = Qa(Ja('String.prototype.indexOf'));
  var zt = function (t, e) {
    var n = Ja(t, !!e);
    if (typeof n == 'function' && tp(t, '.prototype.') > -1) {
      return Qa(n);
    } else {
      return n;
    }
  };
  var np = Ge;
  var ip = np('%TypeError%');
  var rp = function (t, e) {
    if (t == null) {
      throw new ip(e || 'Cannot call method on ' + t);
    }
    return t;
  };
  var di = rp;
  var op = Ge;
  var $a = op('%Array%');
  var sp = !$a.isArray && zt('Object.prototype.toString');
  var ap =
    $a.isArray ||
    function (t) {
      return sp(t) === '[object Array]';
    };
  var el = Ge;
  var lp = zt;
  var up = el('%TypeError%');
  var cp = ap;
  var hp = el('%Reflect.apply%', true) || lp('%Function.prototype.apply%');
  var dp = function (t, e) {
    var n = arguments.length > 2 ? arguments[2] : [];
    if (!cp(n)) {
      throw new up(
        'Assertion failed: optional `argumentsList`, if provided, must be a List'
      );
    }
    return hp(t, e, n);
  };
  var fp = {};
  var pp = Object.freeze({
    __proto__: null,
    [Symbol.toStringTag]: 'Module',
    default: fp,
  });
  var mp = Jn(pp);
  var zr = typeof Map == 'function' && Map.prototype;
  var Vr =
    Object.getOwnPropertyDescriptor && zr
      ? Object.getOwnPropertyDescriptor(Map.prototype, 'size')
      : null;
  var fi = zr && Vr && typeof Vr.get == 'function' ? Vr.get : null;
  var gp = zr && Map.prototype.forEach;
  var Ur = typeof Set == 'function' && Set.prototype;
  var Wr =
    Object.getOwnPropertyDescriptor && Ur
      ? Object.getOwnPropertyDescriptor(Set.prototype, 'size')
      : null;
  var pi = Ur && Wr && typeof Wr.get == 'function' ? Wr.get : null;
  var vp = Ur && Set.prototype.forEach;
  var yp = typeof WeakMap == 'function' && WeakMap.prototype;
  var On = yp ? WeakMap.prototype.has : null;
  var wp = typeof WeakSet == 'function' && WeakSet.prototype;
  var Dn = wp ? WeakSet.prototype.has : null;
  var bp = typeof WeakRef == 'function' && WeakRef.prototype;
  var tl = bp ? WeakRef.prototype.deref : null;
  var Sp = Boolean.prototype.valueOf;
  var Ep = Object.prototype.toString;
  var _p = Function.prototype.toString;
  var xp = String.prototype.match;
  var jr = String.prototype.slice;
  var Pt = String.prototype.replace;
  var Tp = String.prototype.toUpperCase;
  var nl = String.prototype.toLowerCase;
  var il = RegExp.prototype.test;
  var rl = Array.prototype.concat;
  var ft = Array.prototype.join;
  var Cp = Array.prototype.slice;
  var ol = Math.floor;
  var Gr = typeof BigInt == 'function' ? BigInt.prototype.valueOf : null;
  var qr = Object.getOwnPropertySymbols;
  var Xr =
    typeof Symbol == 'function' && typeof Symbol.iterator == 'symbol'
      ? Symbol.prototype.toString
      : null;
  var an = typeof Symbol == 'function' && typeof Symbol.iterator == 'object';
  var Ue =
    typeof Symbol == 'function' &&
    Symbol.toStringTag &&
    (typeof Symbol.toStringTag === an ? 'object' : 'symbol')
      ? Symbol.toStringTag
      : null;
  var sl = Object.prototype.propertyIsEnumerable;
  var al =
    (typeof Reflect == 'function'
      ? Reflect.getPrototypeOf
      : Object.getPrototypeOf) ||
    ([].__proto__ === Array.prototype
      ? function (i) {
          return i.__proto__;
        }
      : null);
  var Yr = mp.custom;
  var Zr = Yr && cl(Yr) ? Yr : null;
  var Pp = function i(t, e, n, r) {
    function d(K, Q, le) {
      if (Q) {
        r = Cp.call(r);
        r.push(Q);
      }
      if (le) {
        var re = { depth: o.depth };
        if (At(o, 'quoteStyle')) {
          re.quoteStyle = o.quoteStyle;
        }
        return i(K, re, n + 1, r);
      }
      return i(K, o, n + 1, r);
    }
    var o = e || {};
    if (
      At(o, 'quoteStyle') &&
      o.quoteStyle !== 'single' &&
      o.quoteStyle !== 'double'
    ) {
      throw new TypeError('option "quoteStyle" must be "single" or "double"');
    }
    if (
      At(o, 'maxStringLength') &&
      (typeof o.maxStringLength == 'number'
        ? o.maxStringLength < 0 && o.maxStringLength !== 1 / 0
        : o.maxStringLength !== null)
    ) {
      throw new TypeError(
        'option "maxStringLength", if provided, must be a positive integer, Infinity, or `null`'
      );
    }
    var s = At(o, 'customInspect') ? o.customInspect : true;
    if (typeof s != 'boolean' && s !== 'symbol') {
      throw new TypeError(
        'option "customInspect", if provided, must be `true`, `false`, or `\'symbol\'`'
      );
    }
    if (
      At(o, 'indent') &&
      o.indent !== null &&
      o.indent !== '\x09' &&
      (parseInt(o.indent, 10) !== o.indent || !(o.indent > 0))
    ) {
      throw new TypeError(
        'option "indent" must be "\\t", an integer > 0, or `null`'
      );
    }
    if (At(o, 'numericSeparator') && typeof o.numericSeparator != 'boolean') {
      throw new TypeError(
        'option "numericSeparator", if provided, must be `true` or `false`'
      );
    }
    var a = o.numericSeparator;
    if (typeof t == 'undefined') {
      return 'undefined';
    }
    if (t === null) {
      return 'null';
    }
    if (typeof t == 'boolean') {
      if (t) {
        return 'true';
      } else {
        return 'false';
      }
    }
    if (typeof t == 'string') {
      return dl(t, o);
    }
    if (typeof t == 'number') {
      if (t === 0) {
        if (1 / 0 / t > 0) {
          return '0';
        } else {
          return '-0';
        }
      }
      var l = String(t);
      if (a) {
        return ll(t, l);
      } else {
        return l;
      }
    }
    if (typeof t == 'bigint') {
      var u = String(t) + 'n';
      if (a) {
        return ll(t, u);
      } else {
        return u;
      }
    }
    var c = typeof o.depth == 'undefined' ? 5 : o.depth;
    if (typeof n == 'undefined') {
      n = 0;
    }
    if (n >= c && c > 0 && typeof t == 'object') {
      if (Kr(t)) {
        return '[Array]';
      } else {
        return '[Object]';
      }
    }
    var h = qp(o, n);
    if (typeof r == 'undefined') {
      r = [];
    } else if (hl(r, t) >= 0) {
      return '[Circular]';
    }
    if (typeof t == 'function') {
      var g = Np(t);
      var y = mi(t, d);
      return (
        '[Function' +
        (g ? ': ' + g : ' (anonymous)') +
        ']' +
        (y.length > 0 ? ' { ' + ft.call(y, ', ') + ' }' : '')
      );
    }
    if (cl(t)) {
      var x = an
        ? Pt.call(String(t), /^(Symbol\(.*\))_[^)]*$/, '$1')
        : Xr.call(t);
      if (typeof t == 'object' && !an) {
        return Rn(x);
      } else {
        return x;
      }
    }
    if (Wp(t)) {
      var b = '<' + nl.call(String(t.nodeName));
      var T = t.attributes || [];
      for (var f = 0; f < T.length; f++) {
        b += ' ' + T[f].name + '=' + ul(Ap(T[f].value), 'double', o);
      }
      b += '>';
      if (t.childNodes && t.childNodes.length) {
        b += '...';
      }
      b += '</' + nl.call(String(t.nodeName)) + '>';
      return b;
    }
    if (Kr(t)) {
      if (t.length === 0) {
        return '[]';
      }
      var E = mi(t, d);
      if (h && !Gp(E)) {
        return '[' + Qr(E, h) + ']';
      } else {
        return '[ ' + ft.call(E, ', ') + ' ]';
      }
    }
    if (Rp(t)) {
      var A = mi(t, d);
      if ('cause' in t && !sl.call(t, 'cause')) {
        return (
          '{ [' +
          String(t) +
          '] ' +
          ft.call(rl.call('[cause]: ' + d(t.cause), A), ', ') +
          ' }'
        );
      } else if (A.length === 0) {
        return '[' + String(t) + ']';
      } else {
        return '{ [' + String(t) + '] ' + ft.call(A, ', ') + ' }';
      }
    }
    if (typeof t == 'object' && s) {
      if (Zr && typeof t[Zr] == 'function') {
        return t[Zr]();
      }
      if (s !== 'symbol' && typeof t.inspect == 'function') {
        return t.inspect();
      }
    }
    if (Ip(t)) {
      var C = [];
      gp.call(t, function (K, Q) {
        C.push(d(Q, t, true) + ' => ' + d(K, t));
      });
      return fl('Map', fi.call(t), C, h);
    }
    if (Vp(t)) {
      var O = [];
      vp.call(t, function (K) {
        O.push(d(K, t));
      });
      return fl('Set', pi.call(t), O, h);
    }
    if (Hp(t)) {
      return Jr('WeakMap');
    }
    if (Up(t)) {
      return Jr('WeakSet');
    }
    if (zp(t)) {
      return Jr('WeakRef');
    }
    if (Fp(t)) {
      return Rn(d(Number(t)));
    }
    if (kp(t)) {
      return Rn(d(Gr.call(t)));
    }
    if (Lp(t)) {
      return Rn(Sp.call(t));
    }
    if (Mp(t)) {
      return Rn(d(String(t)));
    }
    if (!Op(t) && !Dp(t)) {
      var D = mi(t, d);
      var N = al
        ? al(t) === Object.prototype
        : t instanceof Object || t.constructor === Object;
      var B = t instanceof Object ? '' : 'null prototype';
      var Z =
        !N && Ue && Object(t) === t && Ue in t
          ? jr.call(Ot(t), 8, -1)
          : B
          ? 'Object'
          : '';
      var Y =
        N || typeof t.constructor != 'function'
          ? ''
          : t.constructor.name
          ? t.constructor.name + ' '
          : '';
      var U =
        Y +
        (Z || B
          ? '[' + ft.call(rl.call([], Z || [], B || []), ': ') + '] '
          : '');
      if (D.length === 0) {
        return U + '{}';
      } else if (h) {
        return U + '{' + Qr(D, h) + '}';
      } else {
        return U + '{ ' + ft.call(D, ', ') + ' }';
      }
    }
    return String(t);
  };
  var Bp =
    Object.prototype.hasOwnProperty ||
    function (i) {
      return i in this;
    };
  var pl = function (t) {
    return typeof t == 'string' || typeof t == 'symbol';
  };
  var Xp = function (t) {
    if (t === null) {
      return 'Null';
    }
    if (typeof t == 'undefined') {
      return 'Undefined';
    }
    if (typeof t == 'function' || typeof t == 'object') {
      return 'Object';
    }
    if (typeof t == 'number') {
      return 'Number';
    }
    if (typeof t == 'boolean') {
      return 'Boolean';
    }
    if (typeof t == 'string') {
      return 'String';
    }
  };
  var Yp = Xp;
  var $r = function (t) {
    if (typeof t == 'symbol') {
      return 'Symbol';
    } else if (typeof t == 'bigint') {
      return 'BigInt';
    } else {
      return Yp(t);
    }
  };
  var Zp = Ge;
  var ml = Zp('%TypeError%');
  var Kp = Pp;
  var Jp = pl;
  var Qp = $r;
  var gl = function (t, e) {
    if (Qp(t) !== 'Object') {
      throw new ml('Assertion failed: Type(O) is not Object');
    }
    if (!Jp(e)) {
      throw new ml(
        'Assertion failed: IsPropertyKey(P) is not true, got ' + Kp(e)
      );
    }
    return t[e];
  };
  var $p = Ge;
  var vl = $p('%TypeError%');
  var em = pl;
  var tm = $r;
  var nm = function (t, e) {
    if (tm(t) !== 'Object') {
      throw new vl('Assertion failed: `O` must be an Object');
    }
    if (!em(e)) {
      throw new vl('Assertion failed: `P` must be a Property Key');
    }
    return e in t;
  };
  var yl = Function.prototype.toString;
  var ln = typeof Reflect == 'object' && Reflect !== null && Reflect.apply;
  var eo;
  var gi;
  if (typeof ln == 'function' && typeof Object.defineProperty == 'function') {
    try {
      eo = Object.defineProperty({}, 'length', {
        get: function () {
          throw gi;
        },
      });
      gi = {};
      ln(
        function () {
          throw 42;
        },
        null,
        eo
      );
    } catch (i) {
      if (i !== gi) {
        ln = null;
      }
    }
  } else {
    ln = null;
  }
  var im = /^\s*class\b/;
  var to = function (t) {
    try {
      var e = yl.call(t);
      return im.test(e);
    } catch {
      return false;
    }
  };
  var rm = function (t) {
    try {
      if (to(t)) {
        return false;
      } else {
        yl.call(t);
        return true;
      }
    } catch {
      return false;
    }
  };
  var om = Object.prototype.toString;
  var sm = '[object Function]';
  var am = '[object GeneratorFunction]';
  var lm = typeof Symbol == 'function' && !!Symbol.toStringTag;
  var wl =
    typeof document == 'object' &&
    typeof document.all == 'undefined' &&
    document.all !== void 0
      ? document.all
      : {};
  var no = ln
    ? function (t) {
        if (t === wl) {
          return true;
        }
        if (!t || (typeof t != 'function' && typeof t != 'object')) {
          return false;
        }
        if (typeof t == 'function' && !t.prototype) {
          return true;
        }
        try {
          ln(t, null, eo);
        } catch (e) {
          if (e !== gi) {
            return false;
          }
        }
        return !to(t);
      }
    : function (t) {
        if (t === wl) {
          return true;
        }
        if (!t || (typeof t != 'function' && typeof t != 'object')) {
          return false;
        }
        if (typeof t == 'function' && !t.prototype) {
          return true;
        }
        if (lm) {
          return rm(t);
        }
        if (to(t)) {
          return false;
        }
        var e = om.call(t);
        return e === sm || e === am;
      };
  var um = no;
  var bl = Ge;
  var cm = bl('%Math%');
  var hm = bl('%Number%');
  var dm = hm.MAX_SAFE_INTEGER || cm.pow(2, 53) - 1;
  var fm = Ge;
  var pm = fm('%Math.abs%');
  var mm = function (t) {
    return pm(t);
  };
  var gm = Math.floor;
  var vm = function (t) {
    return gm(t);
  };
  var Sl = function (t) {
    return t === null || (typeof t != 'function' && typeof t != 'object');
  };
  var ym = Object.prototype.toString;
  var El = Sl;
  var wm = no;
  var _l = {
    '[[DefaultValue]]': function (i) {
      var t;
      if (arguments.length > 1) {
        t = arguments[1];
      } else {
        t = ym.call(i) === '[object Date]' ? String : Number;
      }
      if (t === String || t === Number) {
        var e =
          t === String ? ['toString', 'valueOf'] : ['valueOf', 'toString'];
        var n;
        var r;
        for (r = 0; r < e.length; ++r) {
          if (wm(i[e[r]])) {
            n = i[e[r]]();
            if (El(n)) {
              return n;
            }
          }
        }
        throw new TypeError('No default value');
      }
      throw new TypeError('invalid [[DefaultValue]] hint supplied');
    },
  };
  var bm = function (t) {
    if (El(t)) {
      return t;
    } else if (arguments.length > 1) {
      return _l['[[DefaultValue]]'](t, arguments[1]);
    } else {
      return _l['[[DefaultValue]]'](t);
    }
  };
  var Sm = bm;
  var Em = Sm;
  var _m = function (t) {
    var e = Em(t, Number);
    if (typeof e != 'string') {
      return +e;
    }
    var n = e.replace(
      /^[ \t\x0b\f\xa0\ufeff\n\r\u2028\u2029\u1680\u180e\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009\u200a\u202f\u205f\u3000\u0085]+|[ \t\x0b\f\xa0\ufeff\n\r\u2028\u2029\u1680\u180e\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009\u200a\u202f\u205f\u3000\u0085]+$/g,
      ''
    );
    if (/^0[ob]|^[+-]0x/.test(n)) {
      return NaN;
    } else {
      return +n;
    }
  };
  var xm =
    Number.isNaN ||
    function (t) {
      return t !== t;
    };
  var Tm =
    Number.isNaN ||
    function (i) {
      return i !== i;
    };
  var Cm =
    Number.isFinite ||
    function (i) {
      return typeof i == 'number' && !Tm(i) && i !== 1 / 0 && i !== -1 / 0;
    };
  var Pm = function (t) {
    if (t >= 0) {
      return 1;
    } else {
      return -1;
    }
  };
  var Am = mm;
  var Om = vm;
  var Dm = _m;
  var Rm = xm;
  var Mm = Cm;
  var Fm = Pm;
  var Lm = function (t) {
    var e = Dm(t);
    if (Rm(e)) {
      return 0;
    } else if (e === 0 || !Mm(e)) {
      return e;
    } else {
      return Fm(e) * Om(Am(e));
    }
  };
  var km = Ge;
  var Bm = km('RegExp.prototype.test');
  var Nm = tn.exports;
  var Im = function (t) {
    return Nm(Bm, t);
  };
  var Hm = function (t) {
    return t === null || (typeof t != 'function' && typeof t != 'object');
  };
  var zm = ja;
  var xl = function () {
    return zm() && !!Symbol.toStringTag;
  };
  var Vm = Date.prototype.getDay;
  var Um = function (t) {
    try {
      Vm.call(t);
      return true;
    } catch {
      return false;
    }
  };
  var Wm = Object.prototype.toString;
  var jm = '[object Date]';
  var Gm = xl();
  var qm = function (t) {
    if (typeof t != 'object' || t === null) {
      return false;
    } else if (Gm) {
      return Um(t);
    } else {
      return Wm.call(t) === jm;
    }
  };
  var io = { exports: {} };
  var Xm = Object.prototype.toString;
  var Ym = qa();
  if (Ym) {
    var Zm = Symbol.prototype.toString;
    var Km = /^Symbol\(.*\)$/;
    var Jm = function (t) {
      if (typeof t.valueOf() == 'symbol') {
        return Km.test(Zm.call(t));
      } else {
        return false;
      }
    };
    io.exports = function (t) {
      if (typeof t == 'symbol') {
        return true;
      }
      if (Xm.call(t) !== '[object Symbol]') {
        return false;
      }
      try {
        return Jm(t);
      } catch {
        return false;
      }
    };
  } else {
    io.exports = function (t) {
      return false;
    };
  }
  var Qm = typeof Symbol == 'function' && typeof Symbol.iterator == 'symbol';
  var ro = Sl;
  var Tl = no;
  var $m = qm;
  var Cl = io.exports;
  var eg = function (t, e) {
    if (typeof t == 'undefined' || t === null) {
      throw new TypeError('Cannot call method on ' + t);
    }
    if (typeof e != 'string' || (e !== 'number' && e !== 'string')) {
      throw new TypeError('hint must be "string" or "number"');
    }
    var n = e === 'string' ? ['toString', 'valueOf'] : ['valueOf', 'toString'];
    var r;
    var o;
    for (var s = 0; s < n.length; ++s) {
      r = t[n[s]];
      if (Tl(r)) {
        o = r.call(t);
        if (ro(o)) {
          return o;
        }
      }
    }
    throw new TypeError('No default value');
  };
  var tg = function (t, e) {
    var n = t[e];
    if (n !== null && typeof n != 'undefined') {
      if (!Tl(n)) {
        throw new TypeError(
          n +
            ' returned for property ' +
            e +
            ' of object ' +
            t +
            ' is not a function'
        );
      }
      return n;
    }
  };
  var ng = function (t) {
    if (ro(t)) {
      return t;
    }
    var e = 'default';
    if (arguments.length > 1) {
      if (arguments[1] === String) {
        e = 'string';
      } else if (arguments[1] === Number) {
        e = 'number';
      }
    }
    var n;
    if (Qm) {
      if (Symbol.toPrimitive) {
        n = tg(t, Symbol.toPrimitive);
      } else if (Cl(t)) {
        n = Symbol.prototype.valueOf;
      }
    }
    if (typeof n != 'undefined') {
      var r = n.call(t, e);
      if (ro(r)) {
        return r;
      }
      throw new TypeError('unable to convert exotic object to primitive');
    }
    if (e === 'default' && ($m(t) || Cl(t))) {
      e = 'string';
    }
    return eg(t, e === 'default' ? 'number' : e);
  };
  var Pl = ng;
  var ig = function (t) {
    if (arguments.length > 1) {
      return Pl(t, arguments[1]);
    } else {
      return Pl(t);
    }
  };
  var vi = Ge;
  var Al = vi('%TypeError%');
  var Ol = vi('%Number%');
  var rg = vi('%RegExp%');
  var Dl = vi('%parseInt%');
  var Rl = zt;
  var yi = Im;
  var og = Hm;
  var Ml = Rl('String.prototype.slice');
  var sg = yi(/^0b[01]+$/i);
  var ag = yi(/^0o[0-7]+$/i);
  var lg = yi(/^[-+]0x[0-9a-f]+$/i);
  var ug = ['\x85', '\u200B', '\uFFFE'].join('');
  var cg = new rg('[' + ug + ']', 'g');
  var hg = yi(cg);
  var Fl = [
    `	
\v\f\r \xA0\u1680\u180E\u2000\u2001\u2002\u2003`,
    '\u2004\u2005\u2006\u2007\u2008\u2009\u200A\u202F\u205F\u3000\u2028',
    '\u2029\uFEFF',
  ].join('');
  var dg = new RegExp('(^[' + Fl + ']+)|([' + Fl + ']+$)', 'g');
  var fg = Rl('String.prototype.replace');
  var pg = function (i) {
    return fg(i, dg, '');
  };
  var mg = ig;
  var gg = function i(t) {
    var e = og(t) ? t : mg(t, Ol);
    if (typeof e == 'symbol') {
      throw new Al('Cannot convert a Symbol value to a number');
    }
    if (typeof e == 'bigint') {
      throw new Al("Conversion from 'BigInt' to 'number' is not allowed.");
    }
    if (typeof e == 'string') {
      if (sg(e)) {
        return i(Dl(Ml(e, 2), 2));
      }
      if (ag(e)) {
        return i(Dl(Ml(e, 2), 8));
      }
      if (hg(e) || lg(e)) {
        return NaN;
      }
      var n = pg(e);
      if (n !== e) {
        return i(n);
      }
    }
    return Ol(e);
  };
  var vg = Lm;
  var yg = gg;
  var wg = function (t) {
    var e = yg(t);
    if (e !== 0) {
      e = vg(e);
    }
    if (e === 0) {
      return 0;
    } else {
      return e;
    }
  };
  var Ll = dm;
  var bg = wg;
  var Sg = function (t) {
    var e = bg(t);
    if (e <= 0) {
      return 0;
    } else if (e > Ll) {
      return Ll;
    } else {
      return e;
    }
  };
  var Eg = Ge;
  var _g = Eg('%TypeError%');
  var xg = gl;
  var Tg = Sg;
  var Cg = $r;
  var Pg = function (t) {
    if (Cg(t) !== 'Object') {
      throw new _g('Assertion failed: `obj` must be an Object');
    }
    return Tg(xg(t, 'length'));
  };
  var Ag = Ge;
  var Og = Ag('%Object%');
  var Dg = di;
  var Rg = function (t) {
    Dg(t);
    return Og(t);
  };
  var kl = Ge;
  var Mg = kl('%String%');
  var Fg = kl('%TypeError%');
  var Bl = function (t) {
    if (typeof t == 'symbol') {
      throw new Fg('Cannot convert a Symbol value to a string');
    }
    return Mg(t);
  };
  var Lg = String.prototype.valueOf;
  var kg = function (t) {
    try {
      Lg.call(t);
      return true;
    } catch {
      return false;
    }
  };
  var Bg = Object.prototype.toString;
  var Ng = '[object String]';
  var Ig = xl();
  var Hg = function (t) {
    if (typeof t == 'string') {
      return true;
    } else if (typeof t == 'object') {
      if (Ig) {
        return kg(t);
      } else {
        return Bg.call(t) === Ng;
      }
    } else {
      return false;
    }
  };
  var zg = Ge;
  var Vg = zt;
  var Ug = zg('%TypeError%');
  var Wg = dp;
  var jg = gl;
  var Gg = nm;
  var qg = um;
  var Xg = Pg;
  var Yg = Rg;
  var Zg = Bl;
  var Kg = Hg;
  var Jg = Vg('String.prototype.split');
  var Nl = Object('a');
  var Qg = Nl[0] !== 'a' || !(0 in Nl);
  var Il = function (t) {
    var e = Yg(this);
    var n = Qg && Kg(this) ? Jg(this, '') : e;
    var r = Xg(n);
    if (!qg(t)) {
      throw new Ug('Array.prototype.forEach callback must be a function');
    }
    var o;
    if (arguments.length > 1) {
      o = arguments[1];
    }
    for (var s = 0; s < r; ) {
      var a = Zg(s);
      var l = Gg(n, a);
      if (l) {
        var u = jg(n, a);
        Wg(t, o, [u, s, n]);
      }
      s += 1;
    }
  };
  var $g = function (t) {
    var e = true;
    var n = true;
    var r = false;
    if (typeof t == 'function') {
      try {
        t.call('f', function (o, s, a) {
          if (typeof a != 'object') {
            e = false;
          }
        });
        t.call(
          [null],
          function () {
            'use strict';
            n = typeof this == 'string';
          },
          'x'
        );
      } catch {
        r = true;
      }
      return !r && e && n;
    }
    return false;
  };
  var ev = $g;
  var tv = Il;
  var Hl = function () {
    var t = Array.prototype.forEach;
    if (ev(t)) {
      return t;
    } else {
      return tv;
    }
  };
  var nv = en;
  var iv = Hl;
  var rv = function () {
    var t = iv();
    nv(
      Array.prototype,
      { forEach: t },
      {
        forEach: function () {
          return Array.prototype.forEach !== t;
        },
      }
    );
    return t;
  };
  var ov = en;
  var sv = tn.exports;
  var av = zt;
  var lv = di;
  var uv = Il;
  var zl = Hl;
  var cv = zl();
  var hv = rv;
  var dv = av('Array.prototype.slice');
  var fv = sv.apply(cv);
  var Vl = function (t, e) {
    lv(t);
    return fv(t, dv(arguments, 1));
  };
  ov(Vl, { getPolyfill: zl, implementation: uv, shim: hv });
  var pv = Vl;
  var mv = di;
  var Ul = zt;
  var gv = Ul('Object.prototype.propertyIsEnumerable');
  var vv = Ul('Array.prototype.push');
  var Wl = function (t) {
    var e = mv(t);
    var n = [];
    for (var r in e) {
      if (gv(e, r)) {
        vv(n, [r, e[r]]);
      }
    }
    return n;
  };
  var yv = Wl;
  var jl = function () {
    if (typeof Object.entries == 'function') {
      return Object.entries;
    } else {
      return yv;
    }
  };
  var wv = jl;
  var bv = en;
  var Sv = function () {
    var t = wv();
    bv(
      Object,
      { entries: t },
      {
        entries: function () {
          return Object.entries !== t;
        },
      }
    );
    return t;
  };
  var Ev = en;
  var _v = tn.exports;
  var xv = Wl;
  var Gl = jl;
  var Tv = Sv;
  var ql = _v(Gl(), Object);
  Ev(ql, { getPolyfill: Gl, implementation: xv, shim: Tv });
  var Cv = ql;
  var Pv = di;
  var Av = Bl;
  var Ov = zt;
  var Xl = Ov('String.prototype.replace');
  var Dv =
    /^[\x09\x0A\x0B\x0C\x0D\x20\xA0\u1680\u180E\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009\u200A\u202F\u205F\u3000\u2028\u2029\uFEFF]+/;
  var Rv =
    /[\x09\x0A\x0B\x0C\x0D\x20\xA0\u1680\u180E\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009\u200A\u202F\u205F\u3000\u2028\u2029\uFEFF]+$/;
  var Yl = function () {
    var t = Av(Pv(this));
    return Xl(Xl(t, Dv, ''), Rv, '');
  };
  var Mv = Yl;
  var Zl = '\u200B';
  var Kl = function () {
    if (String.prototype.trim && Zl.trim() === Zl) {
      return String.prototype.trim;
    } else {
      return Mv;
    }
  };
  var Fv = en;
  var Lv = Kl;
  var kv = function () {
    var t = Lv();
    Fv(
      String.prototype,
      { trim: t },
      {
        trim: function () {
          return String.prototype.trim !== t;
        },
      }
    );
    return t;
  };
  var Bv = tn.exports;
  var Nv = en;
  var Iv = Yl;
  var Jl = Kl;
  var Hv = kv;
  var Ql = Bv(Jl());
  Nv(Ql, { getPolyfill: Jl, implementation: Iv, shim: Hv });
  var zv = Ql;
  var wi = pv;
  var oo = Cv;
  var $l = Xa;
  var Vv = zv;
  var Uv = function (t) {};
  var Wv = String.prototype.replace;
  var eu = String.prototype.split;
  var bi = '||||';
  var so = function (i) {
    var t = i % 100;
    var e = t % 10;
    if (t !== 11 && e === 1) {
      return 0;
    } else if (2 <= e && e <= 4 && (!(t >= 12) || !(t <= 14))) {
      return 1;
    } else {
      return 2;
    }
  };
  var tu = {
    pluralTypes: {
      arabic: function (i) {
        if (i < 3) {
          return i;
        }
        var t = i % 100;
        if (t >= 3 && t <= 10) {
          return 3;
        } else if (t >= 11) {
          return 4;
        } else {
          return 5;
        }
      },
      bosnian_serbian: so,
      chinese: function () {
        return 0;
      },
      croatian: so,
      french: function (i) {
        if (i >= 2) {
          return 1;
        } else {
          return 0;
        }
      },
      german: function (i) {
        if (i === 1) {
          return 0;
        } else {
          return 1;
        }
      },
      russian: so,
      lithuanian: function (i) {
        if (i % 10 == 1 && i % 100 != 11) {
          return 0;
        } else if (
          i % 10 >= 2 &&
          i % 10 <= 9 &&
          (i % 100 < 11 || i % 100 > 19)
        ) {
          return 1;
        } else {
          return 2;
        }
      },
      czech: function (i) {
        if (i === 1) {
          return 0;
        } else if (i >= 2 && i <= 4) {
          return 1;
        } else {
          return 2;
        }
      },
      polish: function (i) {
        if (i === 1) {
          return 0;
        }
        var t = i % 10;
        if (2 <= t && t <= 4 && (i % 100 < 10 || i % 100 >= 20)) {
          return 1;
        } else {
          return 2;
        }
      },
      icelandic: function (i) {
        if (i % 10 != 1 || i % 100 == 11) {
          return 1;
        } else {
          return 0;
        }
      },
      slovenian: function (i) {
        var t = i % 100;
        if (t === 1) {
          return 0;
        } else if (t === 2) {
          return 1;
        } else if (t === 3 || t === 4) {
          return 2;
        } else {
          return 3;
        }
      },
    },
    pluralTypeToLanguages: {
      arabic: ['ar'],
      bosnian_serbian: ['bs-Latn-BA', 'bs-Cyrl-BA', 'srl-RS', 'sr-RS'],
      chinese: [
        'id',
        'id-ID',
        'ja',
        'ko',
        'ko-KR',
        'lo',
        'ms',
        'th',
        'th-TH',
        'zh',
      ],
      croatian: ['hr', 'hr-HR'],
      german: [
        'fa',
        'da',
        'de',
        'en',
        'es',
        'fi',
        'el',
        'he',
        'hi-IN',
        'hu',
        'hu-HU',
        'it',
        'nl',
        'no',
        'pt',
        'sv',
        'tr',
      ],
      french: ['fr', 'tl', 'pt-br'],
      russian: ['ru', 'ru-RU'],
      lithuanian: ['lt'],
      czech: ['cs', 'cs-CZ', 'sk'],
      polish: ['pl'],
      icelandic: ['is'],
      slovenian: ['sl-SL'],
    },
  };
  var Zv = Xv();
  var Kv = /%\{(.*?)\}/g;
  bt.prototype.locale = function (i) {
    if (i) {
      this.currentLocale = i;
    }
    return this.currentLocale;
  };
  bt.prototype.extend = function (i, t) {
    wi(
      oo(i || {}),
      function (e) {
        var n = e[0];
        var r = e[1];
        var o = t ? t + '.' + n : n;
        if (typeof r == 'object') {
          this.extend(r, o);
        } else {
          this.phrases[o] = r;
        }
      },
      this
    );
  };
  bt.prototype.unset = function (i, t) {
    if (typeof i == 'string') {
      delete this.phrases[i];
    } else {
      wi(
        oo(i || {}),
        function (e) {
          var n = e[0];
          var r = e[1];
          var o = t ? t + '.' + n : n;
          if (typeof r == 'object') {
            this.unset(r, o);
          } else {
            delete this.phrases[o];
          }
        },
        this
      );
    }
  };
  bt.prototype.clear = function () {
    this.phrases = {};
  };
  bt.prototype.replace = function (i) {
    this.clear();
    this.extend(i);
  };
  bt.prototype.t = function (i, t) {
    var e;
    var n;
    var r = t == null ? {} : t;
    if (typeof this.phrases[i] == 'string') {
      e = this.phrases[i];
    } else if (typeof r._ == 'string') {
      e = r._;
    } else if (this.onMissingKey) {
      var o = this.onMissingKey;
      n = o(i, r, this.currentLocale, this.tokenRegex, this.pluralRules);
    } else {
      this.warn('Missing translation for key: "' + i + '"');
      n = i;
    }
    if (typeof e == 'string') {
      n = ao(e, r, this.currentLocale, this.tokenRegex, this.pluralRules);
    }
    return n;
  };
  bt.prototype.has = function (i) {
    return $l(this.phrases, i);
  };
  bt.transformPhrase = function (t, e, n) {
    return ao(t, e, n);
  };
  var Jv = bt;
  var Qv = {
    'Add a comment...':
      '\u0625\u0636\u0627\u0641\u0629 \u062A\u0639\u0644\u064A\u0642',
    'Add a reply...': '\u0625\u0636\u0627\u0641\u0629 \u0631\u062F',
    'Add tag...':
      '\u0625\u0636\u0627\u0641\u0629 \u0639\u0644\u0627\u0645\u0629',
    Cancel: '\u0625\u0644\u063A\u0627\u0621',
    Close: '\u0625\u063A\u0644\u0627\u0642',
    Edit: 'Edit',
    Delete: 'Delete',
    Ok: '\u062A\u0645',
  };
  var $v = {
    'Add a comment...': 'Napsat koment\xE1\u0159...',
    'Add a reply...': 'Odpov\u011Bd\u011Bt...',
    'Add tag...': 'P\u0159idat \u0161t\xEDtek...',
    Cancel: 'Zru\u0161it',
    Close: 'Zav\u0159\xEDt',
    Edit: 'Upravit',
    Delete: 'Smazat',
    Ok: 'Ok',
  };
  var ey = {
    'Add a comment...': 'Kommentar schreiben...',
    'Add a reply...': 'Antwort schreiben...',
    'Add tag...': 'Tag...',
    Cancel: 'Abbrechen',
    Close: 'Schliessen',
    Edit: 'Bearbeiten',
    Delete: 'L\xF6schen',
    Ok: 'Ok',
  };
  var ty = {
    'Add a comment...': '\u03A3\u03C7\u03BF\u03BB\u03AF\u03B1\u03C3\u03B5...',
    'Add a reply...': '\u0391\u03C0\u03AC\u03BD\u03C4\u03B7\u03C3\u03B5...',
    'Add tag...': '\u03A0\u03C1\u03CC\u03C3\u03B8\u03B5\u03C3\u03B5 tag...',
    Cancel: '\u0386\u03BA\u03C5\u03C1\u03BF',
    Close: '\u039A\u03BB\u03B5\u03AF\u03C3\u03B9\u03BC\u03BF',
    Edit: '\u0395\u03C0\u03B5\u03BE\u03B5\u03C1\u03B3\u03B1\u03C3\u03AF\u03B1',
    Delete: '\u0394\u03B9\u03B1\u03B3\u03C1\u03B1\u03C6\u03AE',
    Ok: 'Ok',
  };
  var ny = {
    'Add a comment...': 'Agregar un comentario...',
    'Add a reply...': 'Agregar una respuesta...',
    'Add tag...': 'Etiquetar...',
    Cancel: 'Cancelar',
    Close: 'Cerrar',
    Edit: 'Editar',
    Delete: 'Eliminar',
    Ok: 'Ok',
  };
  var iy = {
    'Add a comment...': 'Lis\xE4\xE4 kommentti',
    'Add a reply...': 'Lis\xE4\xE4 vastaus',
    'Add tag...': 'Lis\xE4\xE4 tunniste',
    Cancel: 'Peruuta',
    Close: 'Sulje',
    Edit: 'Muokkaa',
    Delete: 'Poista',
    Ok: 'Ok',
  };
  var ry = {
    'Add a comment...': 'Ajouter un commentaire...',
    'Add a reply...': 'Ajouter une r\xE9ponse...',
    'Add tag...': 'Ajouter une \xE9tiquette...',
    Cancel: 'Annuler',
    Close: 'Fermer',
    Edit: '\xC9diter',
    Delete: 'Supprimer',
    Ok: 'Ok',
  };
  var oy = {
    'Add a comment...': 'Engadir un comentario...',
    'Add a reply...': 'Engadir unha resposta...',
    'Add tag...': 'Etiquetar...',
    Cancel: 'Cancelar',
    Close: 'Pechar',
    Edit: 'Edit',
    Delete: 'Delete',
    Ok: 'Ok',
  };
  var sy = {
    'Add a comment...':
      '\u091F\u093F\u092A\u094D\u092A\u0923\u0940 \u091C\u094B\u0921\u093C\u0947\u0902',
    'Add a reply...': '\u091C\u0935\u093E\u092C \u0926\u0947\u0902',
    'Add tag...': '\u091F\u0948\u0917 \u0932\u0917\u093E\u090F\u0901',
    Cancel: '\u0930\u0926\u094D\u0926 \u0915\u0930\u0947\u0902',
    Close: '\u092C\u0902\u0926 \u0915\u0930\u0947\u0902',
    Edit: '\u0938\u0902\u092A\u093E\u0926\u093F\u0924 \u0915\u0930\u0947\u0902',
    Delete: '\u0939\u091F\u093E\u090F\u0901',
    Ok: '\u0920\u0940\u0915 \u0939\u0948',
  };
  var ay = {
    'Add a comment...': 'Commenta...',
    'Add a reply...': 'Rispondi...',
    'Add tag...': 'Aggiungi tag...',
    Cancel: 'Annulla',
    Close: 'Chiudi',
    Edit: 'Edit',
    Delete: 'Delete',
    Ok: 'Ok',
  };
  var ly = {
    'Add a comment...': '\uB313\uAE00 \uCD94\uAC00',
    'Add a reply...': '\uB2F5\uAE00 \uCD94\uAC00',
    'Add tag...': '\uD0DC\uADF8 \uCD94\uAC00',
    Cancel: '\uCDE8\uC18C',
    Close: '\uB2EB\uAE30',
    Edit: '\uC218\uC815',
    Delete: '\uC0AD\uC81C',
    Ok: '\uD655\uC778',
  };
  var uy = {
    'Add a comment...': 'Commentaar toevoegen...',
    'Add a reply...': 'Antwoord toevoegen...',
    'Add tag...': 'Tag toevoegen...',
    Cancel: 'Afbreken',
    Close: 'Sluiten',
    Edit: 'Bewerken',
    Delete: 'Verwijderen',
    Ok: 'Ok',
  };
  var cy = {
    'Add a comment...': 'Adicionar um coment\xE1rio...',
    'Add a reply...': 'Adicionar uma resposta...',
    'Add tag...': 'Etiquetar...',
    Cancel: 'Cancelar',
    Close: 'Fechar',
    Edit: 'Editar',
    Delete: 'Apagar',
    Ok: 'Ok',
  };
  var hy = {
    'Add a comment...':
      '\u0414\u043E\u0431\u0430\u0432\u0438\u0442\u044C \u043A\u043E\u043C\u043C\u0435\u043D\u0442\u0430\u0440\u0438\u0439...',
    'Add a reply...':
      '\u0414\u043E\u0431\u0430\u0432\u0438\u0442\u044C \u043E\u0442\u0432\u0435\u0442...',
    'Add tag...':
      '\u0414\u043E\u0431\u0430\u0432\u0438\u0442\u044C \u0442\u044D\u0433...',
    Cancel: '\u041E\u0442\u043C\u0435\u043D\u0430',
    Close: '\u0417\u0430\u043A\u0440\u044B\u0442\u044C',
    Edit: '\u0420\u0435\u0434\u0430\u043A\u0442\u0438\u0440\u043E\u0432\u0430\u0442\u044C',
    Delete: '\u0423\u0434\u0430\u043B\u0438\u0442\u044C',
    Ok: '\u041E\u043A',
  };
  var dy = {
    'Add a comment...': 'Skriv en kommentar...',
    'Add a reply...': 'Skriv ett svar...',
    'Add tag...': 'Tagg...',
    Cancel: 'Cancel',
    Close: 'St\xE4ng',
    Edit: 'Edit',
    Delete: 'Delete',
    Ok: 'Ok',
  };
  var fy = {
    'Add a comment...':
      '\u0E40\u0E1E\u0E34\u0E48\u0E21\u0E04\u0E2D\u0E21\u0E40\u0E21\u0E19\u0E15\u0E4C...',
    'Add a reply...': '\u0E15\u0E2D\u0E1A\u0E01\u0E25\u0E31\u0E1A...',
    'Add tag...': '\u0E40\u0E1E\u0E34\u0E48\u0E21\u0E41\u0E17\u0E47\u0E01...',
    Cancel: '\u0E22\u0E01\u0E40\u0E25\u0E34\u0E01',
    Close: '\u0E1B\u0E34\u0E14',
    Edit: '\u0E41\u0E01\u0E49\u0E44\u0E02',
    Delete: '\u0E25\u0E1A',
    Ok: '\u0E15\u0E01\u0E25\u0E07',
  };
  var py = {
    'Add a comment...': 'Yorum ekle...',
    'Add a reply...': 'Cevap ekle...',
    'Add tag...': 'Tag Ekle...',
    Cancel: '\u0130ptal',
    Close: 'Kapat',
    Edit: 'D\xFCzenle',
    Delete: 'Sil',
    Ok: 'Tamam',
  };
  var my = {
    'Add a comment...':
      '\u062A\u0628\u0635\u0631\u06C1 \u06A9\u0631\u06CC\u06BA',
    'Add a reply...': '\u062C\u0648\u0627\u0628 \u062F\u06CC\u06BA',
    'Add tag...': '\u0679\u06CC\u06AF \u0644\u06AF\u0627\u0626\u06CC\u06BA',
    Cancel: '\u0645\u0646\u0633\u0648\u062E \u06A9\u0631\u06CC\u06BA',
    Close: '\u0628\u0646\u062F \u06A9\u0631\u06CC\u06BA',
    Edit: '\u062A\u0631\u0645\u06CC\u0645 \u06A9\u0631\u06CC\u06BA',
    Delete: '\u06C1\u0679\u0627\u0626\u06CC\u06BA',
    Ok: '\u0679\u06BE\u06CC\u06A9 \u06C1\u06D2',
  };
  var iu = {};
  Object.defineProperty(iu, '__esModule', { value: true });
  var lo = [
    [
      '\u062B\u0627\u0646\u064A\u0629',
      '\u062B\u0627\u0646\u064A\u062A\u064A\u0646',
      '%s \u062B\u0648\u0627\u0646',
      '%s \u062B\u0627\u0646\u064A\u0629',
    ],
    [
      '\u062F\u0642\u064A\u0642\u0629',
      '\u062F\u0642\u064A\u0642\u062A\u064A\u0646',
      '%s \u062F\u0642\u0627\u0626\u0642',
      '%s \u062F\u0642\u064A\u0642\u0629',
    ],
    [
      '\u0633\u0627\u0639\u0629',
      '\u0633\u0627\u0639\u062A\u064A\u0646',
      '%s \u0633\u0627\u0639\u0627\u062A',
      '%s \u0633\u0627\u0639\u0629',
    ],
    [
      '\u064A\u0648\u0645',
      '\u064A\u0648\u0645\u064A\u0646',
      '%s \u0623\u064A\u0627\u0645',
      '%s \u064A\u0648\u0645\u0627\u064B',
    ],
    [
      '\u0623\u0633\u0628\u0648\u0639',
      '\u0623\u0633\u0628\u0648\u0639\u064A\u0646',
      '%s \u0623\u0633\u0627\u0628\u064A\u0639',
      '%s \u0623\u0633\u0628\u0648\u0639\u0627\u064B',
    ],
    [
      '\u0634\u0647\u0631',
      '\u0634\u0647\u0631\u064A\u0646',
      '%s \u0623\u0634\u0647\u0631',
      '%s \u0634\u0647\u0631\u0627\u064B',
    ],
    [
      '\u0639\u0627\u0645',
      '\u0639\u0627\u0645\u064A\u0646',
      '%s \u0623\u0639\u0648\u0627\u0645',
      '%s \u0639\u0627\u0645\u0627\u064B',
    ],
  ];
  var yy = (iu.default = vy);
  var ru = {};
  Object.defineProperty(ru, '__esModule', { value: true });
  var by = (ru.default = wy);
  var ou = {};
  Object.defineProperty(ou, '__esModule', { value: true });
  var Ey = (ou.default = Sy);
  var su = {};
  Object.defineProperty(su, '__esModule', { value: true });
  var xy = (su.default = _y);
  var au = {};
  Object.defineProperty(au, '__esModule', { value: true });
  var Cy = (au.default = Ty);
  var lu = {};
  Object.defineProperty(lu, '__esModule', { value: true });
  var Ay = (lu.default = Py);
  var uu = {};
  Object.defineProperty(uu, '__esModule', { value: true });
  var Dy = (uu.default = Oy);
  var cu = {};
  Object.defineProperty(cu, '__esModule', { value: true });
  var My = (cu.default = Ry);
  var hu = {};
  Object.defineProperty(hu, '__esModule', { value: true });
  var Ly = (hu.default = Fy);
  var du = {};
  Object.defineProperty(du, '__esModule', { value: true });
  var By = (du.default = ky);
  var fu = {};
  Object.defineProperty(fu, '__esModule', { value: true });
  var Iy = (fu.default = Ny);
  var pu = {};
  Object.defineProperty(pu, '__esModule', { value: true });
  var zy = (pu.default = Hy);
  var mu = {};
  Object.defineProperty(mu, '__esModule', { value: true });
  var Uy = (mu.default = Vy);
  var gu = {};
  Object.defineProperty(gu, '__esModule', { value: true });
  var vu = Vt.bind(
    null,
    '\u0441\u0435\u043A\u0443\u043D\u0434\u0443',
    '%s \u0441\u0435\u043A\u0443\u043D\u0434\u0443',
    '%s \u0441\u0435\u043A\u0443\u043D\u0434\u044B',
    '%s \u0441\u0435\u043A\u0443\u043D\u0434'
  );
  var yu = Vt.bind(
    null,
    '\u043C\u0438\u043D\u0443\u0442\u0443',
    '%s \u043C\u0438\u043D\u0443\u0442\u0443',
    '%s \u043C\u0438\u043D\u0443\u0442\u044B',
    '%s \u043C\u0438\u043D\u0443\u0442'
  );
  var wu = Vt.bind(
    null,
    '\u0447\u0430\u0441',
    '%s \u0447\u0430\u0441',
    '%s \u0447\u0430\u0441\u0430',
    '%s \u0447\u0430\u0441\u043E\u0432'
  );
  var bu = Vt.bind(
    null,
    '\u0434\u0435\u043D\u044C',
    '%s \u0434\u0435\u043D\u044C',
    '%s \u0434\u043D\u044F',
    '%s \u0434\u043D\u0435\u0439'
  );
  var Su = Vt.bind(
    null,
    '\u043D\u0435\u0434\u0435\u043B\u044E',
    '%s \u043D\u0435\u0434\u0435\u043B\u044E',
    '%s \u043D\u0435\u0434\u0435\u043B\u0438',
    '%s \u043D\u0435\u0434\u0435\u043B\u044C'
  );
  var Eu = Vt.bind(
    null,
    '\u043C\u0435\u0441\u044F\u0446',
    '%s \u043C\u0435\u0441\u044F\u0446',
    '%s \u043C\u0435\u0441\u044F\u0446\u0430',
    '%s \u043C\u0435\u0441\u044F\u0446\u0435\u0432'
  );
  var _u = Vt.bind(
    null,
    '\u0433\u043E\u0434',
    '%s \u0433\u043E\u0434',
    '%s \u0433\u043E\u0434\u0430',
    '%s \u043B\u0435\u0442'
  );
  var jy = (gu.default = Wy);
  var xu = {};
  Object.defineProperty(xu, '__esModule', { value: true });
  var qy = (xu.default = Gy);
  var Tu = {};
  Object.defineProperty(Tu, '__esModule', { value: true });
  var Yy = (Tu.default = Xy);
  var Cu = {};
  Object.defineProperty(Cu, '__esModule', { value: true });
  var Ky = (Cu.default = Zy);
  const Mn = {
    ar: Qv,
    cs: $v,
    de: ey,
    el: ty,
    es: ny,
    fi: iy,
    fr: ry,
    gl: oy,
    hi: sy,
    it: ay,
    ko: ly,
    nl: uy,
    pt: cy,
    ru: hy,
    sv: dy,
    th: fy,
    tr: py,
    ur: my,
  };
  const Ut = new Jv({ allowMissing: true });
  Ut.init = (i, t) => {
    Ut.clear();
    if (i) {
      Ut.locale(i);
      Ut.extend(Mn[i]);
    }
    if (t) {
      Ut.extend(t);
    }
  };
  Re('ar', yy);
  Re('cs', by);
  Re('de', Ey);
  Re('el', xy);
  Re('es', Cy);
  Re('fi', Ay);
  Re('fr', Dy);
  Re('gl', My);
  Re('hi', Ly);
  Re('it', By);
  Re('ko', Iy);
  Re('nl', zy);
  Re('pt', Uy);
  Re('ru', jy);
  Re('sv', qy);
  Re('th', Yy);
  Re('tr', Ky);
  Ut.registerMessages = (i, t) => {
    if (Mn[i]) {
      Mn[i] = ue(ue({}, Mn[i]), t);
    } else {
      Mn[i] = t;
    }
  };
  var Qe = Ut;
  var Jy = (i) => {
    const t = ut();
    Sf(t, () => i.onClickOutside());
    return L.createElement(
      'ul',
      { ref: t, className: 'r6o-comment-dropdown-menu' },
      L.createElement('li', { onClick: i.onEdit }, Qe.t('Edit')),
      L.createElement('li', { onClick: i.onDelete }, Qe.t('Delete'))
    );
  };
  var Pu = {};
  var uo = {};
  var co = { exports: {} };
  (function () {
    var i = co;
    var t = co.exports;
    (function () {
      var e = xt;
      var n = function (e, n) {
        function s(c) {
          function y() {
            var C = window.getComputedStyle(c, null);
            if (C.resize === 'vertical') {
              c.style.resize = 'none';
            } else if (C.resize === 'both') {
              c.style.resize = 'horizontal';
            }
            if (C.boxSizing === 'content-box') {
              h = -(parseFloat(C.paddingTop) + parseFloat(C.paddingBottom));
            } else {
              h =
                parseFloat(C.borderTopWidth) + parseFloat(C.borderBottomWidth);
            }
            if (isNaN(h)) {
              h = 0;
            }
            f();
          }
          function x(C) {
            var O = c.style.width;
            c.style.width = '0px';
            c.offsetWidth;
            c.style.width = O;
            c.style.overflowY = C;
          }
          function b(C) {
            for (
              var O = [];
              C && C.parentNode && C.parentNode instanceof Element;

            ) {
              if (C.parentNode.scrollTop) {
                O.push({
                  node: C.parentNode,
                  scrollTop: C.parentNode.scrollTop,
                });
              }
              C = C.parentNode;
            }
            return O;
          }
          function T() {
            if (c.scrollHeight !== 0) {
              var C = b(c);
              var O =
                document.documentElement && document.documentElement.scrollTop;
              c.style.height = '';
              c.style.height = c.scrollHeight + h + 'px';
              d = c.clientWidth;
              C.forEach(function (D) {
                D.node.scrollTop = D.scrollTop;
              });
              if (O) {
                document.documentElement.scrollTop = O;
              }
            }
          }
          function f() {
            T();
            var C = Math.round(parseFloat(c.style.height));
            var O = window.getComputedStyle(c, null);
            var D =
              O.boxSizing === 'content-box'
                ? Math.round(parseFloat(O.height))
                : c.offsetHeight;
            if (D < C) {
              if (O.overflowY === 'hidden') {
                x('scroll');
                T();
                D =
                  O.boxSizing === 'content-box'
                    ? Math.round(
                        parseFloat(window.getComputedStyle(c, null).height)
                      )
                    : c.offsetHeight;
              }
            } else if (O.overflowY !== 'hidden') {
              x('hidden');
              T();
              D =
                O.boxSizing === 'content-box'
                  ? Math.round(
                      parseFloat(window.getComputedStyle(c, null).height)
                    )
                  : c.offsetHeight;
            }
            if (g !== D) {
              g = D;
              var N = o('autosize:resized');
              try {
                c.dispatchEvent(N);
              } catch {}
            }
          }
          if (!c || !c.nodeName || c.nodeName !== 'TEXTAREA' || r.has(c)) {
            return;
          }
          var h = null;
          var d = null;
          var g = null;
          var E = function () {
            if (c.clientWidth !== d) {
              f();
            }
          };
          var A = function (C) {
            window.removeEventListener('resize', E, false);
            c.removeEventListener('input', f, false);
            c.removeEventListener('keyup', f, false);
            c.removeEventListener('autosize:destroy', A, false);
            c.removeEventListener('autosize:update', f, false);
            Object.keys(C).forEach(function (O) {
              c.style[O] = C[O];
            });
            r.delete(c);
          }.bind(c, {
            height: c.style.height,
            resize: c.style.resize,
            overflowY: c.style.overflowY,
            overflowX: c.style.overflowX,
            wordWrap: c.style.wordWrap,
          });
          c.addEventListener('autosize:destroy', A, false);
          if ('onpropertychange' in c && 'oninput' in c) {
            c.addEventListener('keyup', f, false);
          }
          window.addEventListener('resize', E, false);
          c.addEventListener('input', f, false);
          c.addEventListener('autosize:update', f, false);
          c.style.overflowX = 'hidden';
          c.style.wordWrap = 'break-word';
          r.set(c, { destroy: A, update: f });
          y();
        }
        function a(c) {
          var h = r.get(c);
          if (h) {
            h.destroy();
          }
        }
        function l(c) {
          var h = r.get(c);
          if (h) {
            h.update();
          }
        }
        var r =
          typeof Map == 'function'
            ? new Map()
            : (function () {
                var c = [];
                var h = [];
                return {
                  has: function (g) {
                    return c.indexOf(g) > -1;
                  },
                  get: function (g) {
                    return h[c.indexOf(g)];
                  },
                  set: function (g, y) {
                    if (c.indexOf(g) === -1) {
                      c.push(g);
                      h.push(y);
                    }
                  },
                  delete: function (g) {
                    var y = c.indexOf(g);
                    if (y > -1) {
                      c.splice(y, 1);
                      h.splice(y, 1);
                    }
                  },
                };
              })();
        var o = function (h) {
          return new Event(h, { bubbles: true });
        };
        try {
          new Event('test');
        } catch {
          o = function (d) {
            var g = document.createEvent('Event');
            g.initEvent(d, true, false);
            return g;
          };
        }
        var u = null;
        if (
          typeof window == 'undefined' ||
          typeof window.getComputedStyle != 'function'
        ) {
          u = function (h) {
            return h;
          };
          u.destroy = function (c) {
            return c;
          };
          u.update = function (c) {
            return c;
          };
        } else {
          u = function (h, d) {
            if (h) {
              Array.prototype.forEach.call(h.length ? h : [h], function (g) {
                return s(g);
              });
            }
            return h;
          };
          u.destroy = function (c) {
            if (c) {
              Array.prototype.forEach.call(c.length ? c : [c], a);
            }
            return c;
          };
          u.update = function (c) {
            if (c) {
              Array.prototype.forEach.call(c.length ? c : [c], l);
            }
            return c;
          };
        }
        n.default = u;
        e.exports = n.default;
      };
      n(i, t);
    })();
  })();
  var Qy = function (i, t, e) {
    e = window.getComputedStyle;
    return (e ? e(i) : i.currentStyle)[
      t.replace(/-(\w)/gi, function (n, r) {
        return r.toUpperCase();
      })
    ];
  };
  var $y = Qy;
  var ho = $y;
  var t0 = e0;
  var n0 =
    (xt && xt.__extends) ||
    (function () {
      var i =
        Object.setPrototypeOf ||
        ({ __proto__: [] } instanceof Array &&
          function (t, e) {
            t.__proto__ = e;
          }) ||
        function (t, e) {
          for (var n in e) {
            if (e.hasOwnProperty(n)) {
              t[n] = e[n];
            }
          }
        };
      return function (t, e) {
        function n() {
          this.constructor = t;
        }
        i(t, e);
        t.prototype =
          e === null
            ? Object.create(e)
            : ((n.prototype = e.prototype), new n());
      };
    })();
  var fo =
    (xt && xt.__assign) ||
    Object.assign ||
    function (i) {
      var t;
      var e = 1;
      for (var n = arguments.length; e < n; e++) {
        t = arguments[e];
        for (var r in t) {
          if (Object.prototype.hasOwnProperty.call(t, r)) {
            i[r] = t[r];
          }
        }
      }
      return i;
    };
  var i0 =
    (xt && xt.__rest) ||
    function (i, t) {
      var e = {};
      for (var n in i) {
        if (Object.prototype.hasOwnProperty.call(i, n) && t.indexOf(n) < 0) {
          e[n] = i[n];
        }
      }
      if (i != null && typeof Object.getOwnPropertySymbols == 'function') {
        var r = 0;
        for (var n = Object.getOwnPropertySymbols(i); r < n.length; r++) {
          if (t.indexOf(n[r]) < 0) {
            e[n[r]] = i[n[r]];
          }
        }
      }
      return e;
    };
  uo.__esModule = true;
  var Si = Qt;
  var Fn = Tn.exports;
  var Ei = co.exports;
  var r0 = t0;
  var o0 = r0;
  var Au = 'autosize:resized';
  var s0 = (function () {
    function t() {
      var e = (i !== null && i.apply(this, arguments)) || this;
      e.state = { lineHeight: null };
      e.textarea = null;
      e.onResize = function (n) {
        if (e.props.onResize) {
          e.props.onResize(n);
        }
      };
      e.updateLineHeight = function () {
        if (e.textarea) {
          e.setState({ lineHeight: o0(e.textarea) });
        }
      };
      e.onChange = function (n) {
        var r = e.props.onChange;
        e.currentValue = n.currentTarget.value;
        if (r) {
          r(n);
        }
      };
      return e;
    }
    var i = Si.Component;
    n0(t, i);
    t.prototype.componentDidMount = function () {
      var e = this;
      var n = this.props;
      var r = n.maxRows;
      var o = n.async;
      if (typeof r == 'number') {
        this.updateLineHeight();
      }
      if (typeof r == 'number' || o) {
        setTimeout(function () {
          return e.textarea && Ei(e.textarea);
        });
      } else if (this.textarea) {
        Ei(this.textarea);
      }
      if (this.textarea) {
        this.textarea.addEventListener(Au, this.onResize);
      }
    };
    t.prototype.componentWillUnmount = function () {
      if (this.textarea) {
        this.textarea.removeEventListener(Au, this.onResize);
        Ei.destroy(this.textarea);
      }
    };
    t.prototype.render = function () {
      var e = this;
      var n = this;
      var r = n.props;
      r.onResize;
      var o = r.maxRows;
      r.onChange;
      var s = r.style;
      r.innerRef;
      var a = r.children;
      var l = i0(r, [
        'onResize',
        'maxRows',
        'onChange',
        'style',
        'innerRef',
        'children',
      ]);
      var u = n.state.lineHeight;
      var c = o && u ? u * o : null;
      return Si.createElement(
        'textarea',
        fo({}, l, {
          onChange: this.onChange,
          style: c ? fo({}, s, { maxHeight: c }) : s,
          ref: function (h) {
            e.textarea = h;
            if (typeof e.props.innerRef == 'function') {
              e.props.innerRef(h);
            } else if (e.props.innerRef) {
              e.props.innerRef.current = h;
            }
          },
        }),
        a
      );
    };
    t.prototype.componentDidUpdate = function () {
      if (this.textarea) {
        Ei.update(this.textarea);
      }
    };
    t.defaultProps = { rows: 1, async: false };
    t.propTypes = {
      rows: Fn.number,
      maxRows: Fn.number,
      onResize: Fn.func,
      innerRef: Fn.any,
      async: Fn.bool,
    };
    return t;
  })();
  uo.TextareaAutosize = Si.forwardRef(function (i, t) {
    return Si.createElement(s0, fo({}, i, { innerRef: t }));
  });
  (function () {
    var i = Pu;
    i.__esModule = true;
    var t = uo;
    i.default = t.TextareaAutosize;
  })();
  var a0 = sd(Pu);
  class Ou extends Oe {
    constructor(t) {
      super(t);
      P(this, 'onKeyDown', (t) => {
        if (t.which === 13 && t.ctrlKey) {
          this.props.onSaveAndClose();
        }
      });
      this.element = Xn();
    }
    componentDidMount() {
      if (this.props.focus && this.element.current) {
        this.element.current.focus({ preventScroll: true });
      }
    }
    render() {
      return L.createElement(a0, {
        ref: this.element,
        className: 'r6o-editable-text',
        value: this.props.content,
        placeholder: this.props.placeholder || Qe.t('Add a comment...'),
        disabled: !this.props.editable,
        onChange: this.props.onChange,
        onKeyDown: this.onKeyDown,
      });
    }
  }
  var c0 = (function () {
    function i(e) {
      var n = this;
      this._insertTag = function (r) {
        var o;
        if (n.tags.length === 0) {
          if (n.insertionPoint) {
            o = n.insertionPoint.nextSibling;
          } else if (n.prepend) {
            o = n.container.firstChild;
          } else {
            o = n.before;
          }
        } else {
          o = n.tags[n.tags.length - 1].nextSibling;
        }
        n.container.insertBefore(r, o);
        n.tags.push(r);
      };
      this.isSpeedy = e.speedy === void 0 ? true : e.speedy;
      this.tags = [];
      this.ctr = 0;
      this.nonce = e.nonce;
      this.key = e.key;
      this.container = e.container;
      this.prepend = e.prepend;
      this.insertionPoint = e.insertionPoint;
      this.before = null;
    }
    var t = i.prototype;
    t.hydrate = function (n) {
      n.forEach(this._insertTag);
    };
    t.insert = function (n) {
      if (this.ctr % (this.isSpeedy ? 65e3 : 1) == 0) {
        this._insertTag(u0(this));
      }
      var r = this.tags[this.tags.length - 1];
      if (this.isSpeedy) {
        var o = l0(r);
        try {
          o.insertRule(n, o.cssRules.length);
        } catch {}
      } else {
        r.appendChild(document.createTextNode(n));
      }
      this.ctr++;
    };
    t.flush = function () {
      this.tags.forEach(function (n) {
        return n.parentNode && n.parentNode.removeChild(n);
      });
      this.tags = [];
      this.ctr = 0;
    };
    return i;
  })();
  var We = '-ms-';
  var _i = '-moz-';
  var we = '-webkit-';
  var Du = 'comm';
  var po = 'rule';
  var mo = 'decl';
  var h0 = '@import';
  var Ru = '@keyframes';
  var d0 = Math.abs;
  var xi = String.fromCharCode;
  var f0 = Object.assign;
  var Ci = 1;
  var un = 1;
  var Fu = 0;
  var Xe = 0;
  var De = 0;
  var cn = '';
  var D0 = function (t, e, n) {
    var r = 0;
    for (
      var o = 0;
      (r = o), (o = mt()), r === 38 && o === 12 && (e[n] = 1), !Nn(o);

    ) {
      $e();
    }
    return Bn(t, Xe);
  };
  var R0 = function (t, e) {
    var n = -1;
    var r = 44;
    do {
      switch (Nn(r)) {
        case 0:
          if (r === 38 && mt() === 12) {
            e[n] = 1;
          }
          t[n] += D0(Xe - 1, e, n);
          break;
        case 2:
          t[n] += Oi(r);
          break;
        case 4:
          if (r === 44) {
            t[++n] = mt() === 58 ? '&\x0C' : '';
            e[n] = t[n].length;
            break;
          }
        default:
          t[n] += xi(r);
      }
    } while ((r = $e()));
    return t;
  };
  var M0 = function (t, e) {
    return ku(R0(Lu(t), e));
  };
  var Hu = new WeakMap();
  var F0 = function (t) {
    if (t.type === 'rule' && !!t.parent && !(t.length < 1)) {
      var e = t.value;
      var n = t.parent;
      for (
        var r = t.column === n.column && t.line === n.line;
        n.type !== 'rule';

      ) {
        n = n.parent;
        if (!n) {
          return;
        }
      }
      if (
        (t.props.length !== 1 || e.charCodeAt(0) === 58 || !!Hu.get(n)) &&
        !r
      ) {
        Hu.set(t, true);
        var o = [];
        var s = M0(e, o);
        var a = n.props;
        var l = 0;
        for (var u = 0; l < s.length; l++) {
          for (var c = 0; c < a.length; c++, u++) {
            t.props[u] = o[l] ? s[l].replace(/&\f/g, a[c]) : a[c] + ' ' + s[l];
          }
        }
      }
    }
  };
  var L0 = function (t) {
    if (t.type === 'decl') {
      var e = t.value;
      if (e.charCodeAt(0) === 108 && e.charCodeAt(2) === 98) {
        t.return = '';
        t.value = '';
      }
    }
  };
  var k0 = [A0];
  var B0 = function (t) {
    var e = t.key;
    if (e === 'css') {
      var n = document.querySelectorAll('style[data-emotion]:not([data-s])');
      Array.prototype.forEach.call(n, function (x) {
        var b = x.getAttribute('data-emotion');
        if (b.indexOf(' ') !== -1) {
          document.head.appendChild(x);
          x.setAttribute('data-s', '');
        }
      });
    }
    var r = t.stylisPlugins || k0;
    var o = {};
    var a = [];
    var s = t.container || document.head;
    Array.prototype.forEach.call(
      document.querySelectorAll('style[data-emotion^="' + e + ' "]'),
      function (x) {
        var b = x.getAttribute('data-emotion').split(' ');
        for (var T = 1; T < b.length; T++) {
          o[b[T]] = true;
        }
        a.push(x);
      }
    );
    var u = [F0, L0];
    var c;
    var h = [
      T0,
      P0(function (x) {
        c.insert(x);
      }),
    ];
    var d = C0(u.concat(r, h));
    var g = function (b) {
      return hn(_0(b), d);
    };
    var l = function (b, T, f, E) {
      c = f;
      g(b ? b + '{' + T.styles + '}' : T.styles);
      if (E) {
        y.inserted[T.name] = true;
      }
    };
    var y = {
      key: e,
      sheet: new c0({
        key: e,
        container: s,
        nonce: t.nonce,
        speedy: t.speedy,
        prepend: t.prepend,
        insertionPoint: t.insertionPoint,
      }),
      nonce: t.nonce,
      inserted: o,
      registered: {},
      insert: l,
    };
    y.sheet.hydrate(a);
    return y;
  };
  var zu = { exports: {} };
  var Ee = {};
  var Fe = typeof Symbol == 'function' && Symbol.for;
  var wo = Fe ? Symbol.for('react.element') : 60103;
  var bo = Fe ? Symbol.for('react.portal') : 60106;
  var Ri = Fe ? Symbol.for('react.fragment') : 60107;
  var Mi = Fe ? Symbol.for('react.strict_mode') : 60108;
  var Fi = Fe ? Symbol.for('react.profiler') : 60114;
  var Li = Fe ? Symbol.for('react.provider') : 60109;
  var ki = Fe ? Symbol.for('react.context') : 60110;
  var So = Fe ? Symbol.for('react.async_mode') : 60111;
  var Bi = Fe ? Symbol.for('react.concurrent_mode') : 60111;
  var Ni = Fe ? Symbol.for('react.forward_ref') : 60112;
  var Ii = Fe ? Symbol.for('react.suspense') : 60113;
  var N0 = Fe ? Symbol.for('react.suspense_list') : 60120;
  var Hi = Fe ? Symbol.for('react.memo') : 60115;
  var zi = Fe ? Symbol.for('react.lazy') : 60116;
  var I0 = Fe ? Symbol.for('react.block') : 60121;
  var H0 = Fe ? Symbol.for('react.fundamental') : 60117;
  var z0 = Fe ? Symbol.for('react.responder') : 60118;
  var V0 = Fe ? Symbol.for('react.scope') : 60119;
  Ee.AsyncMode = So;
  Ee.ConcurrentMode = Bi;
  Ee.ContextConsumer = ki;
  Ee.ContextProvider = Li;
  Ee.Element = wo;
  Ee.ForwardRef = Ni;
  Ee.Fragment = Ri;
  Ee.Lazy = zi;
  Ee.Memo = Hi;
  Ee.Portal = bo;
  Ee.Profiler = Fi;
  Ee.StrictMode = Mi;
  Ee.Suspense = Ii;
  Ee.isAsyncMode = function (i) {
    return Vu(i) || et(i) === So;
  };
  Ee.isConcurrentMode = Vu;
  Ee.isContextConsumer = function (i) {
    return et(i) === ki;
  };
  Ee.isContextProvider = function (i) {
    return et(i) === Li;
  };
  Ee.isElement = function (i) {
    return typeof i == 'object' && i !== null && i.$$typeof === wo;
  };
  Ee.isForwardRef = function (i) {
    return et(i) === Ni;
  };
  Ee.isFragment = function (i) {
    return et(i) === Ri;
  };
  Ee.isLazy = function (i) {
    return et(i) === zi;
  };
  Ee.isMemo = function (i) {
    return et(i) === Hi;
  };
  Ee.isPortal = function (i) {
    return et(i) === bo;
  };
  Ee.isProfiler = function (i) {
    return et(i) === Fi;
  };
  Ee.isStrictMode = function (i) {
    return et(i) === Mi;
  };
  Ee.isSuspense = function (i) {
    return et(i) === Ii;
  };
  Ee.isValidElementType = function (i) {
    return (
      typeof i == 'string' ||
      typeof i == 'function' ||
      i === Ri ||
      i === Bi ||
      i === Fi ||
      i === Mi ||
      i === Ii ||
      i === N0 ||
      (typeof i == 'object' &&
        i !== null &&
        (i.$$typeof === zi ||
          i.$$typeof === Hi ||
          i.$$typeof === Li ||
          i.$$typeof === ki ||
          i.$$typeof === Ni ||
          i.$$typeof === H0 ||
          i.$$typeof === z0 ||
          i.$$typeof === V0 ||
          i.$$typeof === I0))
    );
  };
  Ee.typeOf = et;
  zu.exports = Ee;
  var Uu = zu.exports;
  var U0 = {
    $$typeof: true,
    render: true,
    defaultProps: true,
    displayName: true,
    propTypes: true,
  };
  var W0 = {
    $$typeof: true,
    compare: true,
    defaultProps: true,
    displayName: true,
    propTypes: true,
    type: true,
  };
  var Wu = {};
  Wu[Uu.ForwardRef] = U0;
  Wu[Uu.Memo] = W0;
  var j0 = true;
  var Gu = function (t, e, n) {
    var r = t.key + '-' + e.name;
    if ((n === false || j0 === false) && t.registered[r] === void 0) {
      t.registered[r] = e.styles;
    }
    if (t.inserted[e.name] === void 0) {
      var o = e;
      do {
        t.insert(e === o ? '.' + r : '', o, t.sheet, true);
        o = o.next;
      } while (o !== void 0);
    }
  };
  var q0 = {
    animationIterationCount: 1,
    borderImageOutset: 1,
    borderImageSlice: 1,
    borderImageWidth: 1,
    boxFlex: 1,
    boxFlexGroup: 1,
    boxOrdinalGroup: 1,
    columnCount: 1,
    columns: 1,
    flex: 1,
    flexGrow: 1,
    flexPositive: 1,
    flexShrink: 1,
    flexNegative: 1,
    flexOrder: 1,
    gridRow: 1,
    gridRowEnd: 1,
    gridRowSpan: 1,
    gridRowStart: 1,
    gridColumn: 1,
    gridColumnEnd: 1,
    gridColumnSpan: 1,
    gridColumnStart: 1,
    msGridRow: 1,
    msGridRowSpan: 1,
    msGridColumn: 1,
    msGridColumnSpan: 1,
    fontWeight: 1,
    lineHeight: 1,
    opacity: 1,
    order: 1,
    orphans: 1,
    tabSize: 1,
    widows: 1,
    zIndex: 1,
    zoom: 1,
    WebkitLineClamp: 1,
    fillOpacity: 1,
    floodOpacity: 1,
    stopOpacity: 1,
    strokeDasharray: 1,
    strokeDashoffset: 1,
    strokeMiterlimit: 1,
    strokeOpacity: 1,
    strokeWidth: 1,
  };
  var X0 = /[A-Z]|^ms/g;
  var Y0 = /_EMO_([^_]+?)_([^]*?)_EMO_/g;
  var qu = function (t) {
    return t.charCodeAt(1) === 45;
  };
  var Xu = function (t) {
    return t != null && typeof t != 'boolean';
  };
  var Eo = O0(function (i) {
    if (qu(i)) {
      return i;
    } else {
      return i.replace(X0, '-$&').toLowerCase();
    }
  });
  var Yu = function (t, e) {
    switch (t) {
      case 'animation':
      case 'animationName':
        if (typeof e == 'string') {
          return e.replace(Y0, function (n, r, o) {
            gt = { name: r, styles: o, next: gt };
            return r;
          });
        }
    }
    if (q0[t] !== 1 && !qu(t) && typeof e == 'number' && e !== 0) {
      return e + 'px';
    } else {
      return e;
    }
  };
  var Zu = /label:\s*([^\s;\n{]+)\s*(;|$)/g;
  var gt;
  var _o = function (t, e, n) {
    if (
      t.length === 1 &&
      typeof t[0] == 'object' &&
      t[0] !== null &&
      t[0].styles !== void 0
    ) {
      return t[0];
    }
    var r = true;
    var o = '';
    gt = void 0;
    var s = t[0];
    if (s == null || s.raw === void 0) {
      r = false;
      o += In(n, e, s);
    } else {
      o += s[0];
    }
    for (var a = 1; a < t.length; a++) {
      o += In(n, e, t[a]);
      if (r) {
        o += s[a];
      }
    }
    Zu.lastIndex = 0;
    var l = '';
    for (var u; (u = Zu.exec(o)) !== null; ) {
      l += '-' + u[1];
    }
    var c = G0(o) + l;
    return { name: c, styles: o, next: gt };
  };
  var xo = {}.hasOwnProperty;
  var Ku = Yt(typeof HTMLElement != 'undefined' ? B0({ key: 'css' }) : null);
  Ku.Provider;
  var Ju = function (t) {
    return yr(function (e, n) {
      var r = En(Ku);
      return t(e, r, n);
    });
  };
  var Qu = Yt({});
  var To = '__EMOTION_TYPE_PLEASE_DO_NOT_USE__';
  var K0 = function (t, e) {
    var n = {};
    for (var r in e) {
      if (xo.call(e, r)) {
        n[r] = e[r];
      }
    }
    n[To] = t;
    return n;
  };
  var J0 = function () {
    return null;
  };
  var Q0 = Ju(function (i, t, e) {
    var n = i.css;
    if (typeof n == 'string' && t.registered[n] !== void 0) {
      n = t.registered[n];
    }
    var r = i[To];
    var o = [n];
    var s = '';
    if (typeof i.className == 'string') {
      s = ju(t.registered, o, i.className);
    } else if (i.className != null) {
      s = i.className + ' ';
    }
    var a = _o(o, void 0, En(Qu));
    Gu(t, a, typeof r == 'string');
    s += t.key + '-' + a.name;
    var l = {};
    for (var u in i) {
      if (xo.call(i, u) && u !== 'css' && u !== To) {
        l[u] = i[u];
      }
    }
    l.ref = e;
    l.className = s;
    var c = Ae(r, l);
    var h = Ae(J0, null);
    return Ae(Ye, null, h, c);
  });
  var ie = function (t, e) {
    var n = arguments;
    if (e == null || !xo.call(e, 'css')) {
      return Ae.apply(void 0, n);
    }
    var r = n.length;
    var o = new Array(r);
    o[0] = Q0;
    o[1] = K0(t, e);
    for (var s = 2; s < r; s++) {
      o[s] = n[s];
    }
    return Ae.apply(null, o);
  };
  var $0 = function () {
    var t = Co.apply(void 0, arguments);
    var e = 'animation-' + t.name;
    return {
      name: e,
      styles: '@keyframes ' + e + '{' + t.styles + '}',
      anim: 1,
      toString: function () {
        return '_EMO_' + this.name + '_' + this.styles + '_EMO_';
      },
    };
  };
  var ew = function i(t) {
    var e = t.length;
    var n = 0;
    for (var r = ''; n < e; n++) {
      var o = t[n];
      if (o != null) {
        var s = void 0;
        switch (typeof o) {
          case 'boolean':
            break;
          case 'object':
            if (Array.isArray(o)) {
              s = i(o);
            } else {
              s = '';
              for (var a in o) {
                if (o[a] && a) {
                  if (s) {
                    s += ' ';
                  }
                  s += a;
                }
              }
            }
            break;
          default:
            s = o;
        }
        if (s) {
          if (r) {
            r += ' ';
          }
          r += s;
        }
      }
    }
    return r;
  };
  var nw = function () {
    return null;
  };
  var iw = Ju(function (i, t) {
    var e = false;
    var n = function () {
      var u = arguments.length;
      var c = new Array(u);
      for (var h = 0; h < u; h++) {
        c[h] = arguments[h];
      }
      var d = _o(c, t.registered);
      Gu(t, d, false);
      return t.key + '-' + d.name;
    };
    var r = function () {
      var u = arguments.length;
      var c = new Array(u);
      for (var h = 0; h < u; h++) {
        c[h] = arguments[h];
      }
      return tw(t.registered, n, ew(c));
    };
    var o = { css: n, cx: r, theme: En(Qu) };
    var s = i.children(o);
    e = true;
    var a = Ae(nw, null);
    return Ae(Ye, null, a, s);
  });
  var $u = {};
  Object.defineProperty($u, '__esModule', { value: true });
  var Oo =
    Object.assign ||
    function (i) {
      for (var t = 1; t < arguments.length; t++) {
        var e = arguments[t];
        for (var n in e) {
          if (Object.prototype.hasOwnProperty.call(e, n)) {
            i[n] = e[n];
          }
        }
      }
      return i;
    };
  var ec = (function () {
    function i(t, e) {
      for (var n = 0; n < e.length; n++) {
        var r = e[n];
        r.enumerable = r.enumerable || false;
        r.configurable = true;
        if ('value' in r) {
          r.writable = true;
        }
        Object.defineProperty(t, r.key, r);
      }
    }
    return function (t, e, n) {
      if (e) {
        i(t.prototype, e);
      }
      if (n) {
        i(t, n);
      }
      return t;
    };
  })();
  var tc = Qt;
  var Hn = nc(tc);
  var ow = Tn.exports;
  var Me = nc(ow);
  var ic = {
    position: 'absolute',
    top: 0,
    left: 0,
    visibility: 'hidden',
    height: 0,
    overflow: 'scroll',
    whiteSpace: 'pre',
  };
  var cw = [
    'extraWidth',
    'injectStyles',
    'inputClassName',
    'inputRef',
    'inputStyle',
    'minWidth',
    'onAutosize',
    'placeholderIsMinWidth',
  ];
  var hw = function (t) {
    cw.forEach(function (e) {
      return delete t[e];
    });
    return t;
  };
  var rc = function (t, e) {
    e.style.fontSize = t.fontSize;
    e.style.fontFamily = t.fontFamily;
    e.style.fontWeight = t.fontWeight;
    e.style.fontStyle = t.fontStyle;
    e.style.letterSpacing = t.letterSpacing;
    e.style.textTransform = t.textTransform;
  };
  var oc =
    typeof window != 'undefined' && window.navigator
      ? /MSIE |Trident\/|Edge\//.test(window.navigator.userAgent)
      : false;
  var sc = function () {
    if (oc) {
      return '_' + Math.random().toString(36).substr(2, 12);
    } else {
      return;
    }
  };
  var Do = (function () {
    function t(e) {
      aw(this, t);
      var n = lw(this, (t.__proto__ || Object.getPrototypeOf(t)).call(this, e));
      n.inputRef = function (r) {
        n.input = r;
        if (typeof n.props.inputRef == 'function') {
          n.props.inputRef(r);
        }
      };
      n.placeHolderSizerRef = function (r) {
        n.placeHolderSizer = r;
      };
      n.sizerRef = function (r) {
        n.sizer = r;
      };
      n.state = { inputWidth: e.minWidth, inputId: e.id || sc(), prevId: e.id };
      return n;
    }
    var i = tc.Component;
    uw(t, i);
    ec(t, null, [
      {
        key: 'getDerivedStateFromProps',
        value: function (n, r) {
          var o = n.id;
          if (o === r.prevId) {
            return null;
          } else {
            return { inputId: o || sc(), prevId: o };
          }
        },
      },
    ]);
    ec(t, [
      {
        key: 'componentDidMount',
        value: function () {
          this.mounted = true;
          this.copyInputStyles();
          this.updateInputWidth();
        },
      },
      {
        key: 'componentDidUpdate',
        value: function (n, r) {
          if (
            r.inputWidth !== this.state.inputWidth &&
            typeof this.props.onAutosize == 'function'
          ) {
            this.props.onAutosize(this.state.inputWidth);
          }
          this.updateInputWidth();
        },
      },
      {
        key: 'componentWillUnmount',
        value: function () {
          this.mounted = false;
        },
      },
      {
        key: 'copyInputStyles',
        value: function () {
          if (!!this.mounted && !!window.getComputedStyle) {
            var n = this.input && window.getComputedStyle(this.input);
            if (n) {
              rc(n, this.sizer);
              if (this.placeHolderSizer) {
                rc(n, this.placeHolderSizer);
              }
            }
          }
        },
      },
      {
        key: 'updateInputWidth',
        value: function () {
          if (
            !!this.mounted &&
            !!this.sizer &&
            typeof this.sizer.scrollWidth != 'undefined'
          ) {
            var n = void 0;
            if (
              this.props.placeholder &&
              (!this.props.value ||
                (this.props.value && this.props.placeholderIsMinWidth))
            ) {
              n =
                Math.max(
                  this.sizer.scrollWidth,
                  this.placeHolderSizer.scrollWidth
                ) + 2;
            } else {
              n = this.sizer.scrollWidth + 2;
            }
            var r =
              this.props.type === 'number' && this.props.extraWidth === void 0
                ? 16
                : parseInt(this.props.extraWidth) || 0;
            n += r;
            if (n < this.props.minWidth) {
              n = this.props.minWidth;
            }
            if (n !== this.state.inputWidth) {
              this.setState({ inputWidth: n });
            }
          }
        },
      },
      {
        key: 'getInput',
        value: function () {
          return this.input;
        },
      },
      {
        key: 'focus',
        value: function () {
          this.input.focus();
        },
      },
      {
        key: 'blur',
        value: function () {
          this.input.blur();
        },
      },
      {
        key: 'select',
        value: function () {
          this.input.select();
        },
      },
      {
        key: 'renderStyles',
        value: function () {
          var n = this.props.injectStyles;
          if (oc && n) {
            return Hn.default.createElement('style', {
              dangerouslySetInnerHTML: {
                __html:
                  'input#' +
                  this.state.inputId +
                  '::-ms-clear {display: none;}',
              },
            });
          } else {
            return null;
          }
        },
      },
      {
        key: 'render',
        value: function () {
          var n = [this.props.defaultValue, this.props.value, ''].reduce(
            function (a, l) {
              if (a == null) {
                return l;
              } else {
                return a;
              }
            }
          );
          var r = Oo({}, this.props.style);
          if (!r.display) {
            r.display = 'inline-block';
          }
          var o = Oo(
            { boxSizing: 'content-box', width: this.state.inputWidth + 'px' },
            this.props.inputStyle
          );
          var s = sw(this.props, []);
          hw(s);
          s.className = this.props.inputClassName;
          s.id = this.state.inputId;
          s.style = o;
          return Hn.default.createElement(
            'div',
            { className: this.props.className, style: r },
            this.renderStyles(),
            Hn.default.createElement(
              'input',
              Oo({}, s, { ref: this.inputRef })
            ),
            Hn.default.createElement(
              'div',
              { ref: this.sizerRef, style: ic },
              n
            ),
            this.props.placeholder
              ? Hn.default.createElement(
                  'div',
                  { ref: this.placeHolderSizerRef, style: ic },
                  this.props.placeholder
                )
              : null
          );
        },
      },
    ]);
    return t;
  })();
  Do.propTypes = {
    className: Me.default.string,
    defaultValue: Me.default.any,
    extraWidth: Me.default.oneOfType([Me.default.number, Me.default.string]),
    id: Me.default.string,
    injectStyles: Me.default.bool,
    inputClassName: Me.default.string,
    inputRef: Me.default.func,
    inputStyle: Me.default.object,
    minWidth: Me.default.oneOfType([Me.default.number, Me.default.string]),
    onAutosize: Me.default.func,
    onChange: Me.default.func,
    placeholder: Me.default.string,
    placeholderIsMinWidth: Me.default.bool,
    style: Me.default.object,
    value: Me.default.any,
  };
  Do.defaultProps = { minWidth: 1, injectStyles: true };
  var dw = ($u.default = Do);
  var Xi = function () {};
  var uc = function (t) {
    if (Array.isArray(t)) {
      return t.filter(Boolean);
    } else if (Ao(t) === 'object' && t !== null) {
      return [t];
    } else {
      return [];
    }
  };
  var cc = function (t) {
    t.className;
    t.clearValue;
    t.cx;
    t.getStyles;
    t.getValue;
    t.hasValue;
    t.isMulti;
    t.isRtl;
    t.options;
    t.selectOption;
    t.selectProps;
    t.setValue;
    t.theme;
    var e = dn(t, [
      'className',
      'clearValue',
      'cx',
      'getStyles',
      'getValue',
      'hasValue',
      'isMulti',
      'isRtl',
      'options',
      'selectOption',
      'selectProps',
      'setValue',
      'theme',
    ]);
    return Ne({}, e);
  };
  var fc = false;
  var xw = {
    get passive() {
      return (fc = true);
    },
  };
  var Ki = typeof window != 'undefined' ? window : {};
  if (Ki.addEventListener && Ki.removeEventListener) {
    Ki.addEventListener('p', Xi, xw);
    Ki.removeEventListener('p', Xi, false);
  }
  var Tw = fc;
  var Mo = function (t) {
    if (t === 'auto') {
      return 'bottom';
    } else {
      return t;
    }
  };
  var Aw = function (t) {
    var n = t.placement;
    var r = t.theme;
    var o = r.borderRadius;
    var s = r.spacing;
    var a = r.colors;
    var e = { label: 'menu' };
    St(e, Pw(n), '100%');
    St(e, 'backgroundColor', a.neutral0);
    St(e, 'borderRadius', o);
    St(
      e,
      'boxShadow',
      '0 0 0 1px hsla(0, 0%, 0%, 0.1), 0 4px 11px hsla(0, 0%, 0%, 0.1)'
    );
    St(e, 'marginBottom', s.menuGutter);
    St(e, 'marginTop', s.menuGutter);
    St(e, 'position', 'absolute');
    St(e, 'width', '100%');
    St(e, 'zIndex', 1);
    return e;
  };
  var pc = Yt({ getPortalPlacement: null });
  var mc = (function () {
    function e() {
      Vi(this, e);
      var r = arguments.length;
      var o = new Array(r);
      for (var s = 0; s < r; s++) {
        o[s] = arguments[s];
      }
      var n = t.call.apply(t, [this].concat(o));
      n.state = { maxHeight: n.props.maxMenuHeight, placement: null };
      n.getPlacement = function (a) {
        var l = n.props;
        var u = l.minMenuHeight;
        var c = l.maxMenuHeight;
        var h = l.menuPlacement;
        var d = l.menuPosition;
        var g = l.menuShouldScrollIntoView;
        var y = l.theme;
        if (a) {
          var x = d === 'fixed';
          var b = g && !x;
          var T = Cw({
            maxHeight: c,
            menuEl: a,
            minHeight: u,
            placement: h,
            shouldScroll: b,
            isFixedPosition: x,
            theme: y,
          });
          var f = n.context.getPortalPlacement;
          if (f) {
            f(T);
          }
          n.setState(T);
        }
      };
      n.getUpdatedProps = function () {
        var a = n.props.menuPlacement;
        var l = n.state.placement || Mo(a);
        return Ne(
          Ne({}, n.props),
          {},
          { placement: l, maxHeight: n.state.maxHeight }
        );
      };
      return n;
    }
    var i = Oe;
    ji(e, i);
    var t = qi(e);
    Ui(e, [
      {
        key: 'render',
        value: function () {
          var r = this.props.children;
          return r({
            ref: this.getPlacement,
            placerProps: this.getUpdatedProps(),
          });
        },
      },
    ]);
    return e;
  })();
  mc.contextType = pc;
  var Ow = function (t) {
    var e = t.children;
    var n = t.className;
    var r = t.cx;
    var o = t.getStyles;
    var s = t.innerRef;
    var a = t.innerProps;
    return ie(
      'div',
      ne({ css: o('menu', t), className: r({ menu: true }, n), ref: s }, a),
      e
    );
  };
  var Dw = function (t) {
    var e = t.maxHeight;
    var n = t.theme.spacing.baseUnit;
    return {
      maxHeight: e,
      overflowY: 'auto',
      paddingBottom: n,
      paddingTop: n,
      position: 'relative',
      WebkitOverflowScrolling: 'touch',
    };
  };
  var Rw = function (t) {
    var e = t.children;
    var n = t.className;
    var r = t.cx;
    var o = t.getStyles;
    var s = t.innerProps;
    var a = t.innerRef;
    var l = t.isMulti;
    return ie(
      'div',
      ne(
        {
          css: o('menuList', t),
          className: r({ 'menu-list': true, 'menu-list--is-multi': l }, n),
          ref: a,
        },
        s
      ),
      e
    );
  };
  var gc = function (t) {
    var e = t.theme;
    var n = e.spacing.baseUnit;
    var r = e.colors;
    return {
      color: r.neutral40,
      padding: ''.concat(n * 2, 'px ').concat(n * 3, 'px'),
      textAlign: 'center',
    };
  };
  var Mw = gc;
  var Fw = gc;
  var vc = function (t) {
    var e = t.children;
    var n = t.className;
    var r = t.cx;
    var o = t.getStyles;
    var s = t.innerProps;
    return ie(
      'div',
      ne(
        {
          css: o('noOptionsMessage', t),
          className: r(
            { 'menu-notice': true, 'menu-notice--no-options': true },
            n
          ),
        },
        s
      ),
      e
    );
  };
  vc.defaultProps = { children: 'No options' };
  var yc = function (t) {
    var e = t.children;
    var n = t.className;
    var r = t.cx;
    var o = t.getStyles;
    var s = t.innerProps;
    return ie(
      'div',
      ne(
        {
          css: o('loadingMessage', t),
          className: r(
            { 'menu-notice': true, 'menu-notice--loading': true },
            n
          ),
        },
        s
      ),
      e
    );
  };
  yc.defaultProps = { children: 'Loading...' };
  var Lw = function (t) {
    var e = t.rect;
    var n = t.offset;
    var r = t.position;
    return { left: e.left, position: r, top: n, width: e.width, zIndex: 1 };
  };
  var kw = (function () {
    function e() {
      Vi(this, e);
      var r = arguments.length;
      var o = new Array(r);
      for (var s = 0; s < r; s++) {
        o[s] = arguments[s];
      }
      var n = t.call.apply(t, [this].concat(o));
      n.state = { placement: null };
      n.getPortalPlacement = function (a) {
        var l = a.placement;
        var u = Mo(n.props.menuPlacement);
        if (l !== u) {
          n.setState({ placement: l });
        }
      };
      return n;
    }
    var i = Oe;
    ji(e, i);
    var t = qi(e);
    Ui(e, [
      {
        key: 'render',
        value: function () {
          var r = this.props;
          var o = r.appendTo;
          var s = r.children;
          var a = r.className;
          var l = r.controlElement;
          var u = r.cx;
          var c = r.innerProps;
          var h = r.menuPlacement;
          var d = r.menuPosition;
          var g = r.getStyles;
          var y = d === 'fixed';
          if ((!o && !y) || !l) {
            return null;
          }
          var x = this.state.placement || Mo(h);
          var b = Ew(l);
          var T = y ? 0 : window.pageYOffset;
          var f = b[x] + T;
          var E = { offset: f, position: d, rect: b };
          var A = ie(
            'div',
            ne(
              {
                css: g('menuPortal', E),
                className: u({ 'menu-portal': true }, a),
              },
              c
            ),
            s
          );
          return ie(
            pc.Provider,
            { value: { getPortalPlacement: this.getPortalPlacement } },
            o ? wr(A, o) : A
          );
        },
      },
    ]);
    return e;
  })();
  var Bw = function (t) {
    var e = t.isDisabled;
    var n = t.isRtl;
    return {
      label: 'container',
      direction: n ? 'rtl' : null,
      pointerEvents: e ? 'none' : null,
      position: 'relative',
    };
  };
  var Nw = function (t) {
    var e = t.children;
    var n = t.className;
    var r = t.cx;
    var o = t.getStyles;
    var s = t.innerProps;
    var a = t.isDisabled;
    var l = t.isRtl;
    return ie(
      'div',
      ne(
        {
          css: o('container', t),
          className: r({ '--is-disabled': a, '--is-rtl': l }, n),
        },
        s
      ),
      e
    );
  };
  var Iw = function (t) {
    var e = t.theme.spacing;
    return {
      alignItems: 'center',
      display: 'flex',
      flex: 1,
      flexWrap: 'wrap',
      padding: ''.concat(e.baseUnit / 2, 'px ').concat(e.baseUnit * 2, 'px'),
      WebkitOverflowScrolling: 'touch',
      position: 'relative',
      overflow: 'hidden',
    };
  };
  var Hw = function (t) {
    var e = t.children;
    var n = t.className;
    var r = t.cx;
    var o = t.innerProps;
    var s = t.isMulti;
    var a = t.getStyles;
    var l = t.hasValue;
    return ie(
      'div',
      ne(
        {
          css: a('valueContainer', t),
          className: r(
            {
              'value-container': true,
              'value-container--is-multi': s,
              'value-container--has-value': l,
            },
            n
          ),
        },
        o
      ),
      e
    );
  };
  var zw = function () {
    return {
      alignItems: 'center',
      alignSelf: 'stretch',
      display: 'flex',
      flexShrink: 0,
    };
  };
  var Vw = function (t) {
    var e = t.children;
    var n = t.className;
    var r = t.cx;
    var o = t.innerProps;
    var s = t.getStyles;
    return ie(
      'div',
      ne(
        {
          css: s('indicatorsContainer', t),
          className: r({ indicators: true }, n),
        },
        o
      ),
      e
    );
  };
  var wc;
  var Uw = {
    name: '8mmkcg',
    styles:
      'display:inline-block;fill:currentColor;line-height:1;stroke:currentColor;stroke-width:0',
  };
  var bc = function (t) {
    var e = t.size;
    var n = dn(t, ['size']);
    return ie(
      'svg',
      ne(
        {
          height: e,
          width: e,
          viewBox: '0 0 20 20',
          'aria-hidden': 'true',
          focusable: 'false',
          css: Uw,
        },
        n
      )
    );
  };
  var Fo = function (t) {
    return ie(
      bc,
      ne({ size: 20 }, t),
      ie('path', {
        d: 'M14.348 14.849c-0.469 0.469-1.229 0.469-1.697 0l-2.651-3.030-2.651 3.029c-0.469 0.469-1.229 0.469-1.697 0-0.469-0.469-0.469-1.229 0-1.697l2.758-3.15-2.759-3.152c-0.469-0.469-0.469-1.228 0-1.697s1.228-0.469 1.697 0l2.652 3.031 2.651-3.031c0.469-0.469 1.228-0.469 1.697 0s0.469 1.229 0 1.697l-2.758 3.152 2.758 3.15c0.469 0.469 0.469 1.229 0 1.698z',
      })
    );
  };
  var Sc = function (t) {
    return ie(
      bc,
      ne({ size: 20 }, t),
      ie('path', {
        d: 'M4.516 7.548c0.436-0.446 1.043-0.481 1.576 0l3.908 3.747 3.908-3.747c0.533-0.481 1.141-0.446 1.574 0 0.436 0.445 0.408 1.197 0 1.615-0.406 0.418-4.695 4.502-4.695 4.502-0.217 0.223-0.502 0.335-0.787 0.335s-0.57-0.112-0.789-0.335c0 0-4.287-4.084-4.695-4.502s-0.436-1.17 0-1.615z',
      })
    );
  };
  var Ec = function (t) {
    var e = t.isFocused;
    var n = t.theme;
    var r = n.spacing.baseUnit;
    var o = n.colors;
    return {
      label: 'indicatorContainer',
      color: e ? o.neutral60 : o.neutral20,
      display: 'flex',
      padding: r * 2,
      transition: 'color 150ms',
      ':hover': { color: e ? o.neutral80 : o.neutral40 },
    };
  };
  var Ww = Ec;
  var jw = function (t) {
    var e = t.children;
    var n = t.className;
    var r = t.cx;
    var o = t.getStyles;
    var s = t.innerProps;
    return ie(
      'div',
      ne(
        {
          css: o('dropdownIndicator', t),
          className: r({ indicator: true, 'dropdown-indicator': true }, n),
        },
        s
      ),
      e || ie(Sc, null)
    );
  };
  var Gw = Ec;
  var qw = function (t) {
    var e = t.children;
    var n = t.className;
    var r = t.cx;
    var o = t.getStyles;
    var s = t.innerProps;
    return ie(
      'div',
      ne(
        {
          css: o('clearIndicator', t),
          className: r({ indicator: true, 'clear-indicator': true }, n),
        },
        s
      ),
      e || ie(Fo, null)
    );
  };
  var Xw = function (t) {
    var e = t.isDisabled;
    var n = t.theme;
    var r = n.spacing.baseUnit;
    var o = n.colors;
    return {
      label: 'indicatorSeparator',
      alignSelf: 'stretch',
      backgroundColor: e ? o.neutral10 : o.neutral20,
      marginBottom: r * 2,
      marginTop: r * 2,
      width: 1,
    };
  };
  var Yw = function (t) {
    var e = t.className;
    var n = t.cx;
    var r = t.getStyles;
    var o = t.innerProps;
    return ie(
      'span',
      ne({}, o, {
        css: r('indicatorSeparator', t),
        className: n({ 'indicator-separator': true }, e),
      })
    );
  };
  var Zw = $0(
    wc ||
      (wc = rw([
        `
  0%, 80%, 100% { opacity: 0; }
  40% { opacity: 1; }
`,
      ]))
  );
  var Kw = function (t) {
    var e = t.isFocused;
    var n = t.size;
    var r = t.theme;
    var o = r.colors;
    var s = r.spacing.baseUnit;
    return {
      label: 'loadingIndicator',
      color: e ? o.neutral60 : o.neutral20,
      display: 'flex',
      padding: s * 2,
      transition: 'color 150ms',
      alignSelf: 'center',
      fontSize: n,
      lineHeight: 1,
      marginRight: n,
      textAlign: 'center',
      verticalAlign: 'middle',
    };
  };
  var Lo = function (t) {
    var e = t.delay;
    var n = t.offset;
    return ie('span', {
      css: Co(
        {
          animation: ''
            .concat(Zw, ' 1s ease-in-out ')
            .concat(e, 'ms infinite;'),
          backgroundColor: 'currentColor',
          borderRadius: '1em',
          display: 'inline-block',
          marginLeft: n ? '1em' : null,
          height: '1em',
          verticalAlign: 'top',
          width: '1em',
        },
        '',
        ''
      ),
    });
  };
  var _c = function (t) {
    var e = t.className;
    var n = t.cx;
    var r = t.getStyles;
    var o = t.innerProps;
    var s = t.isRtl;
    return ie(
      'div',
      ne(
        {
          css: r('loadingIndicator', t),
          className: n({ indicator: true, 'loading-indicator': true }, e),
        },
        o
      ),
      ie(Lo, { delay: 0, offset: s }),
      ie(Lo, { delay: 160, offset: true }),
      ie(Lo, { delay: 320, offset: !s })
    );
  };
  _c.defaultProps = { size: 4 };
  var Jw = function (t) {
    var e = t.isDisabled;
    var n = t.isFocused;
    var r = t.theme;
    var o = r.colors;
    var s = r.borderRadius;
    var a = r.spacing;
    return {
      label: 'control',
      alignItems: 'center',
      backgroundColor: e ? o.neutral5 : o.neutral0,
      borderColor: e ? o.neutral10 : n ? o.primary : o.neutral20,
      borderRadius: s,
      borderStyle: 'solid',
      borderWidth: 1,
      boxShadow: n ? '0 0 0 1px '.concat(o.primary) : null,
      cursor: 'default',
      display: 'flex',
      flexWrap: 'wrap',
      justifyContent: 'space-between',
      minHeight: a.controlHeight,
      outline: '0 !important',
      position: 'relative',
      transition: 'all 100ms',
      '&:hover': { borderColor: n ? o.primary : o.neutral30 },
    };
  };
  var Qw = function (t) {
    var e = t.children;
    var n = t.cx;
    var r = t.getStyles;
    var o = t.className;
    var s = t.isDisabled;
    var a = t.isFocused;
    var l = t.innerRef;
    var u = t.innerProps;
    var c = t.menuIsOpen;
    return ie(
      'div',
      ne(
        {
          ref: l,
          css: r('control', t),
          className: n(
            {
              control: true,
              'control--is-disabled': s,
              'control--is-focused': a,
              'control--menu-is-open': c,
            },
            o
          ),
        },
        u
      ),
      e
    );
  };
  var $w = function (t) {
    var e = t.theme.spacing;
    return { paddingBottom: e.baseUnit * 2, paddingTop: e.baseUnit * 2 };
  };
  var eb = function (t) {
    var e = t.children;
    var n = t.className;
    var r = t.cx;
    var o = t.getStyles;
    var s = t.Heading;
    var a = t.headingProps;
    var l = t.innerProps;
    var u = t.label;
    var c = t.theme;
    var h = t.selectProps;
    return ie(
      'div',
      ne({ css: o('group', t), className: r({ group: true }, n) }, l),
      ie(s, ne({}, a, { selectProps: h, theme: c, getStyles: o, cx: r }), u),
      ie('div', null, e)
    );
  };
  var tb = function (t) {
    var e = t.theme.spacing;
    return {
      label: 'group',
      color: '#999',
      cursor: 'default',
      display: 'block',
      fontSize: '75%',
      fontWeight: '500',
      marginBottom: '0.25em',
      paddingLeft: e.baseUnit * 3,
      paddingRight: e.baseUnit * 3,
      textTransform: 'uppercase',
    };
  };
  var nb = function (t) {
    var e = t.getStyles;
    var n = t.cx;
    var r = t.className;
    var o = cc(t);
    o.data;
    var s = dn(o, ['data']);
    return ie(
      'div',
      ne(
        {
          css: e('groupHeading', t),
          className: n({ 'group-heading': true }, r),
        },
        s
      )
    );
  };
  var ib = function (t) {
    var e = t.isDisabled;
    var n = t.theme;
    var r = n.spacing;
    var o = n.colors;
    return {
      margin: r.baseUnit / 2,
      paddingBottom: r.baseUnit / 2,
      paddingTop: r.baseUnit / 2,
      visibility: e ? 'hidden' : 'visible',
      color: o.neutral80,
    };
  };
  var rb = function (t) {
    return {
      label: 'input',
      background: 0,
      border: 0,
      fontSize: 'inherit',
      opacity: t ? 0 : 1,
      outline: 0,
      padding: 0,
      color: 'inherit',
    };
  };
  var ob = function (t) {
    var e = t.className;
    var n = t.cx;
    var r = t.getStyles;
    var o = cc(t);
    var s = o.innerRef;
    var a = o.isDisabled;
    var l = o.isHidden;
    var u = dn(o, ['innerRef', 'isDisabled', 'isHidden']);
    return ie(
      'div',
      { css: r('input', t) },
      ie(
        dw,
        ne(
          {
            className: n({ input: true }, e),
            inputRef: s,
            inputStyle: rb(l),
            disabled: a,
          },
          u
        )
      )
    );
  };
  var sb = function (t) {
    var e = t.theme;
    var n = e.spacing;
    var r = e.borderRadius;
    var o = e.colors;
    return {
      label: 'multiValue',
      backgroundColor: o.neutral10,
      borderRadius: r / 2,
      display: 'flex',
      margin: n.baseUnit / 2,
      minWidth: 0,
    };
  };
  var ab = function (t) {
    var e = t.theme;
    var n = e.borderRadius;
    var r = e.colors;
    var o = t.cropWithEllipsis;
    return {
      borderRadius: n / 2,
      color: r.neutral80,
      fontSize: '85%',
      overflow: 'hidden',
      padding: 3,
      paddingLeft: 6,
      textOverflow: o ? 'ellipsis' : null,
      whiteSpace: 'nowrap',
    };
  };
  var lb = function (t) {
    var e = t.theme;
    var n = e.spacing;
    var r = e.borderRadius;
    var o = e.colors;
    var s = t.isFocused;
    return {
      alignItems: 'center',
      borderRadius: r / 2,
      backgroundColor: s && o.dangerLight,
      display: 'flex',
      paddingLeft: n.baseUnit,
      paddingRight: n.baseUnit,
      ':hover': { backgroundColor: o.dangerLight, color: o.danger },
    };
  };
  var xc = function (t) {
    var e = t.children;
    var n = t.innerProps;
    return ie('div', n, e);
  };
  var ub = xc;
  var cb = xc;
  var Tc = function (t) {
    var e = t.children;
    var n = t.className;
    var r = t.components;
    var o = t.cx;
    var s = t.data;
    var a = t.getStyles;
    var l = t.innerProps;
    var u = t.isDisabled;
    var c = t.removeProps;
    var h = t.selectProps;
    var d = r.Container;
    var g = r.Label;
    var y = r.Remove;
    return ie(iw, null, function (x) {
      var b = x.css;
      var T = x.cx;
      return ie(
        d,
        {
          data: s,
          innerProps: Ne(
            {
              className: T(
                b(a('multiValue', t)),
                o({ 'multi-value': true, 'multi-value--is-disabled': u }, n)
              ),
            },
            l
          ),
          selectProps: h,
        },
        ie(
          g,
          {
            data: s,
            innerProps: {
              className: T(
                b(a('multiValueLabel', t)),
                o({ 'multi-value__label': true }, n)
              ),
            },
            selectProps: h,
          },
          e
        ),
        ie(y, {
          data: s,
          innerProps: Ne(
            {
              className: T(
                b(a('multiValueRemove', t)),
                o({ 'multi-value__remove': true }, n)
              ),
            },
            c
          ),
          selectProps: h,
        })
      );
    });
  };
  Tc.defaultProps = { cropWithEllipsis: true };
  var db = function (t) {
    var e = t.isDisabled;
    var n = t.isFocused;
    var r = t.isSelected;
    var o = t.theme;
    var s = o.spacing;
    var a = o.colors;
    return {
      label: 'option',
      backgroundColor: r ? a.primary : n ? a.primary25 : 'transparent',
      color: e ? a.neutral20 : r ? a.neutral0 : 'inherit',
      cursor: 'default',
      display: 'block',
      fontSize: 'inherit',
      padding: ''.concat(s.baseUnit * 2, 'px ').concat(s.baseUnit * 3, 'px'),
      width: '100%',
      userSelect: 'none',
      WebkitTapHighlightColor: 'rgba(0, 0, 0, 0)',
      ':active': { backgroundColor: !e && (r ? a.primary : a.primary50) },
    };
  };
  var fb = function (t) {
    var e = t.children;
    var n = t.className;
    var r = t.cx;
    var o = t.getStyles;
    var s = t.isDisabled;
    var a = t.isFocused;
    var l = t.isSelected;
    var u = t.innerRef;
    var c = t.innerProps;
    return ie(
      'div',
      ne(
        {
          css: o('option', t),
          className: r(
            {
              option: true,
              'option--is-disabled': s,
              'option--is-focused': a,
              'option--is-selected': l,
            },
            n
          ),
          ref: u,
        },
        c
      ),
      e
    );
  };
  var pb = function (t) {
    var e = t.theme;
    var n = e.spacing;
    var r = e.colors;
    return {
      label: 'placeholder',
      color: r.neutral50,
      marginLeft: n.baseUnit / 2,
      marginRight: n.baseUnit / 2,
      position: 'absolute',
      top: '50%',
      transform: 'translateY(-50%)',
    };
  };
  var mb = function (t) {
    var e = t.children;
    var n = t.className;
    var r = t.cx;
    var o = t.getStyles;
    var s = t.innerProps;
    return ie(
      'div',
      ne(
        { css: o('placeholder', t), className: r({ placeholder: true }, n) },
        s
      ),
      e
    );
  };
  var gb = function (t) {
    var e = t.isDisabled;
    var n = t.theme;
    var r = n.spacing;
    var o = n.colors;
    return {
      label: 'singleValue',
      color: e ? o.neutral40 : o.neutral80,
      marginLeft: r.baseUnit / 2,
      marginRight: r.baseUnit / 2,
      maxWidth: 'calc(100% - '.concat(r.baseUnit * 2, 'px)'),
      overflow: 'hidden',
      position: 'absolute',
      textOverflow: 'ellipsis',
      whiteSpace: 'nowrap',
      top: '50%',
      transform: 'translateY(-50%)',
    };
  };
  var vb = function (t) {
    var e = t.children;
    var n = t.className;
    var r = t.cx;
    var o = t.getStyles;
    var s = t.isDisabled;
    var a = t.innerProps;
    return ie(
      'div',
      ne(
        {
          css: o('singleValue', t),
          className: r(
            { 'single-value': true, 'single-value--is-disabled': s },
            n
          ),
        },
        a
      ),
      e
    );
  };
  var yb = {
    ClearIndicator: qw,
    Control: Qw,
    DropdownIndicator: jw,
    DownChevron: Sc,
    CrossIcon: Fo,
    Group: eb,
    GroupHeading: nb,
    IndicatorsContainer: Vw,
    IndicatorSeparator: Yw,
    Input: ob,
    LoadingIndicator: _c,
    Menu: Ow,
    MenuList: Rw,
    MenuPortal: kw,
    LoadingMessage: yc,
    NoOptionsMessage: vc,
    MultiValue: Tc,
    MultiValueContainer: ub,
    MultiValueLabel: cb,
    MultiValueRemove: hb,
    Option: fb,
    Placeholder: mb,
    SelectContainer: Nw,
    SingleValue: vb,
    ValueContainer: Hw,
  };
  var wb = function (t) {
    return Ne(Ne({}, yb), t.components);
  };
  var Pc =
    Number.isNaN ||
    function (t) {
      return typeof t == 'number' && t !== t;
    };
  var Pb = {
    name: '7pg0cj-a11yText',
    styles:
      'label:a11yText;z-index:9999;border:0;clip:rect(1px, 1px, 1px, 1px);height:1px;width:1px;position:absolute;overflow:hidden;padding:0;white-space:nowrap',
  };
  var Ab = function (t) {
    return ie('span', ne({ css: Pb }, t));
  };
  var Ob = {
    guidance: function (t) {
      var e = t.isSearchable;
      var n = t.isMulti;
      var r = t.isDisabled;
      var o = t.tabSelectsValue;
      var s = t.context;
      switch (s) {
        case 'menu':
          return 'Use Up and Down to choose options'
            .concat(
              r ? '' : ', press Enter to select the currently focused option',
              ', press Escape to exit the menu'
            )
            .concat(
              o ? ', press Tab to select the option and exit the menu' : '',
              '.'
            );
        case 'input':
          return ''
            .concat(t['aria-label'] || 'Select', ' is focused ')
            .concat(
              e ? ',type to refine list' : '',
              ', press Down to open the menu, '
            )
            .concat(n ? ' press left to focus selected values' : '');
        case 'value':
          return 'Use left and right to toggle between focused values, press Backspace to remove the currently focused value';
        default:
          return '';
      }
    },
    onChange: function (t) {
      var e = t.action;
      var n = t.label;
      var r = n === void 0 ? '' : n;
      var o = t.isDisabled;
      switch (e) {
        case 'deselect-option':
        case 'pop-value':
        case 'remove-value':
          return 'option '.concat(r, ', deselected.');
        case 'select-option':
          if (o) {
            return 'option '.concat(r, ' is disabled. Select another option.');
          } else {
            return 'option '.concat(r, ', selected.');
          }
        default:
          return '';
      }
    },
    onFocus: function (t) {
      var e = t.context;
      var n = t.focused;
      var r = n === void 0 ? {} : n;
      var o = t.options;
      var s = t.label;
      var a = s === void 0 ? '' : s;
      var l = t.selectValue;
      var u = t.isDisabled;
      var c = t.isSelected;
      var h = function (x, b) {
        if (x && x.length) {
          return ''.concat(x.indexOf(b) + 1, ' of ').concat(x.length);
        } else {
          return '';
        }
      };
      if (e === 'value' && l) {
        return 'value '.concat(a, ' focused, ').concat(h(l, r), '.');
      }
      if (e === 'menu') {
        var d = u ? ' disabled' : '';
        var g = ''.concat(c ? 'selected' : 'focused').concat(d);
        return 'option '.concat(a, ' ').concat(g, ', ').concat(h(o, r), '.');
      }
      return '';
    },
    onFilter: function (t) {
      var e = t.inputValue;
      var n = t.resultsMessage;
      return ''.concat(n).concat(e ? ' for search term ' + e : '', '.');
    },
  };
  var Db = function (t) {
    var e = t.ariaSelection;
    var n = t.focusedOption;
    var r = t.focusedValue;
    var o = t.focusableOptions;
    var s = t.isFocused;
    var a = t.selectValue;
    var l = t.selectProps;
    var u = l.ariaLiveMessages;
    var c = l.getOptionLabel;
    var h = l.inputValue;
    var d = l.isMulti;
    var g = l.isOptionDisabled;
    var y = l.isSearchable;
    var x = l.menuIsOpen;
    var b = l.options;
    var T = l.screenReaderStatus;
    var f = l.tabSelectsValue;
    var E = l['aria-label'];
    var A = l['aria-live'];
    var C = wt(
      function () {
        return Ne(Ne({}, Ob), u || {});
      },
      [u]
    );
    var O = wt(
      function () {
        var Y = '';
        if (e && C.onChange) {
          var U = e.option;
          var K = e.removedValue;
          var Q = e.value;
          var le = function (me) {
            if (Array.isArray(me)) {
              return null;
            } else {
              return me;
            }
          };
          var re = K || U || le(Q);
          var se = Ne({ isDisabled: re && g(re), label: re ? c(re) : '' }, e);
          Y = C.onChange(se);
        }
        return Y;
      },
      [e, g, c, C]
    );
    var D = wt(
      function () {
        var Y = '';
        var U = n || r;
        var K = !!n && !!a && !!a.includes(n);
        if (U && C.onFocus) {
          var Q = {
            focused: U,
            label: c(U),
            isDisabled: g(U),
            isSelected: K,
            options: b,
            context: U === n ? 'menu' : 'value',
            selectValue: a,
          };
          Y = C.onFocus(Q);
        }
        return Y;
      },
      [n, r, c, g, C, b, a]
    );
    var N = wt(
      function () {
        var Y = '';
        if (x && b.length && C.onFilter) {
          var U = T({ count: o.length });
          Y = C.onFilter({ inputValue: h, resultsMessage: U });
        }
        return Y;
      },
      [o, h, x, C, b, T]
    );
    var B = wt(
      function () {
        var Y = '';
        if (C.guidance) {
          var U = r ? 'value' : x ? 'menu' : 'input';
          Y = C.guidance({
            'aria-label': E,
            context: U,
            isDisabled: n && g(n),
            isMulti: d,
            isSearchable: y,
            tabSelectsValue: f,
          });
        }
        return Y;
      },
      [E, n, r, d, g, y, x, C, f]
    );
    var Z = ''.concat(D, ' ').concat(N, ' ').concat(B);
    return ie(
      Ab,
      {
        'aria-live': A,
        'aria-atomic': 'false',
        'aria-relevant': 'additions text',
      },
      s &&
        ie(
          L.Fragment,
          null,
          ie('span', { id: 'aria-selection' }, O),
          ie('span', { id: 'aria-context' }, Z)
        )
    );
  };
  var Bo = [
    {
      base: 'A',
      letters:
        'A\u24B6\uFF21\xC0\xC1\xC2\u1EA6\u1EA4\u1EAA\u1EA8\xC3\u0100\u0102\u1EB0\u1EAE\u1EB4\u1EB2\u0226\u01E0\xC4\u01DE\u1EA2\xC5\u01FA\u01CD\u0200\u0202\u1EA0\u1EAC\u1EB6\u1E00\u0104\u023A\u2C6F',
    },
    { base: 'AA', letters: '\uA732' },
    { base: 'AE', letters: '\xC6\u01FC\u01E2' },
    { base: 'AO', letters: '\uA734' },
    { base: 'AU', letters: '\uA736' },
    { base: 'AV', letters: '\uA738\uA73A' },
    { base: 'AY', letters: '\uA73C' },
    { base: 'B', letters: 'B\u24B7\uFF22\u1E02\u1E04\u1E06\u0243\u0182\u0181' },
    {
      base: 'C',
      letters:
        'C\u24B8\uFF23\u0106\u0108\u010A\u010C\xC7\u1E08\u0187\u023B\uA73E',
    },
    {
      base: 'D',
      letters:
        'D\u24B9\uFF24\u1E0A\u010E\u1E0C\u1E10\u1E12\u1E0E\u0110\u018B\u018A\u0189\uA779',
    },
    { base: 'DZ', letters: '\u01F1\u01C4' },
    { base: 'Dz', letters: '\u01F2\u01C5' },
    {
      base: 'E',
      letters:
        'E\u24BA\uFF25\xC8\xC9\xCA\u1EC0\u1EBE\u1EC4\u1EC2\u1EBC\u0112\u1E14\u1E16\u0114\u0116\xCB\u1EBA\u011A\u0204\u0206\u1EB8\u1EC6\u0228\u1E1C\u0118\u1E18\u1E1A\u0190\u018E',
    },
    { base: 'F', letters: 'F\u24BB\uFF26\u1E1E\u0191\uA77B' },
    {
      base: 'G',
      letters:
        'G\u24BC\uFF27\u01F4\u011C\u1E20\u011E\u0120\u01E6\u0122\u01E4\u0193\uA7A0\uA77D\uA77E',
    },
    {
      base: 'H',
      letters:
        'H\u24BD\uFF28\u0124\u1E22\u1E26\u021E\u1E24\u1E28\u1E2A\u0126\u2C67\u2C75\uA78D',
    },
    {
      base: 'I',
      letters:
        'I\u24BE\uFF29\xCC\xCD\xCE\u0128\u012A\u012C\u0130\xCF\u1E2E\u1EC8\u01CF\u0208\u020A\u1ECA\u012E\u1E2C\u0197',
    },
    { base: 'J', letters: 'J\u24BF\uFF2A\u0134\u0248' },
    {
      base: 'K',
      letters:
        'K\u24C0\uFF2B\u1E30\u01E8\u1E32\u0136\u1E34\u0198\u2C69\uA740\uA742\uA744\uA7A2',
    },
    {
      base: 'L',
      letters:
        'L\u24C1\uFF2C\u013F\u0139\u013D\u1E36\u1E38\u013B\u1E3C\u1E3A\u0141\u023D\u2C62\u2C60\uA748\uA746\uA780',
    },
    { base: 'LJ', letters: '\u01C7' },
    { base: 'Lj', letters: '\u01C8' },
    { base: 'M', letters: 'M\u24C2\uFF2D\u1E3E\u1E40\u1E42\u2C6E\u019C' },
    {
      base: 'N',
      letters:
        'N\u24C3\uFF2E\u01F8\u0143\xD1\u1E44\u0147\u1E46\u0145\u1E4A\u1E48\u0220\u019D\uA790\uA7A4',
    },
    { base: 'NJ', letters: '\u01CA' },
    { base: 'Nj', letters: '\u01CB' },
    {
      base: 'O',
      letters:
        'O\u24C4\uFF2F\xD2\xD3\xD4\u1ED2\u1ED0\u1ED6\u1ED4\xD5\u1E4C\u022C\u1E4E\u014C\u1E50\u1E52\u014E\u022E\u0230\xD6\u022A\u1ECE\u0150\u01D1\u020C\u020E\u01A0\u1EDC\u1EDA\u1EE0\u1EDE\u1EE2\u1ECC\u1ED8\u01EA\u01EC\xD8\u01FE\u0186\u019F\uA74A\uA74C',
    },
    { base: 'OI', letters: '\u01A2' },
    { base: 'OO', letters: '\uA74E' },
    { base: 'OU', letters: '\u0222' },
    {
      base: 'P',
      letters: 'P\u24C5\uFF30\u1E54\u1E56\u01A4\u2C63\uA750\uA752\uA754',
    },
    { base: 'Q', letters: 'Q\u24C6\uFF31\uA756\uA758\u024A' },
    {
      base: 'R',
      letters:
        'R\u24C7\uFF32\u0154\u1E58\u0158\u0210\u0212\u1E5A\u1E5C\u0156\u1E5E\u024C\u2C64\uA75A\uA7A6\uA782',
    },
    {
      base: 'S',
      letters:
        'S\u24C8\uFF33\u1E9E\u015A\u1E64\u015C\u1E60\u0160\u1E66\u1E62\u1E68\u0218\u015E\u2C7E\uA7A8\uA784',
    },
    {
      base: 'T',
      letters:
        'T\u24C9\uFF34\u1E6A\u0164\u1E6C\u021A\u0162\u1E70\u1E6E\u0166\u01AC\u01AE\u023E\uA786',
    },
    { base: 'TZ', letters: '\uA728' },
    {
      base: 'U',
      letters:
        'U\u24CA\uFF35\xD9\xDA\xDB\u0168\u1E78\u016A\u1E7A\u016C\xDC\u01DB\u01D7\u01D5\u01D9\u1EE6\u016E\u0170\u01D3\u0214\u0216\u01AF\u1EEA\u1EE8\u1EEE\u1EEC\u1EF0\u1EE4\u1E72\u0172\u1E76\u1E74\u0244',
    },
    { base: 'V', letters: 'V\u24CB\uFF36\u1E7C\u1E7E\u01B2\uA75E\u0245' },
    { base: 'VY', letters: '\uA760' },
    {
      base: 'W',
      letters: 'W\u24CC\uFF37\u1E80\u1E82\u0174\u1E86\u1E84\u1E88\u2C72',
    },
    { base: 'X', letters: 'X\u24CD\uFF38\u1E8A\u1E8C' },
    {
      base: 'Y',
      letters:
        'Y\u24CE\uFF39\u1EF2\xDD\u0176\u1EF8\u0232\u1E8E\u0178\u1EF6\u1EF4\u01B3\u024E\u1EFE',
    },
    {
      base: 'Z',
      letters:
        'Z\u24CF\uFF3A\u0179\u1E90\u017B\u017D\u1E92\u1E94\u01B5\u0224\u2C7F\u2C6B\uA762',
    },
    {
      base: 'a',
      letters:
        'a\u24D0\uFF41\u1E9A\xE0\xE1\xE2\u1EA7\u1EA5\u1EAB\u1EA9\xE3\u0101\u0103\u1EB1\u1EAF\u1EB5\u1EB3\u0227\u01E1\xE4\u01DF\u1EA3\xE5\u01FB\u01CE\u0201\u0203\u1EA1\u1EAD\u1EB7\u1E01\u0105\u2C65\u0250',
    },
    { base: 'aa', letters: '\uA733' },
    { base: 'ae', letters: '\xE6\u01FD\u01E3' },
    { base: 'ao', letters: '\uA735' },
    { base: 'au', letters: '\uA737' },
    { base: 'av', letters: '\uA739\uA73B' },
    { base: 'ay', letters: '\uA73D' },
    { base: 'b', letters: 'b\u24D1\uFF42\u1E03\u1E05\u1E07\u0180\u0183\u0253' },
    {
      base: 'c',
      letters:
        'c\u24D2\uFF43\u0107\u0109\u010B\u010D\xE7\u1E09\u0188\u023C\uA73F\u2184',
    },
    {
      base: 'd',
      letters:
        'd\u24D3\uFF44\u1E0B\u010F\u1E0D\u1E11\u1E13\u1E0F\u0111\u018C\u0256\u0257\uA77A',
    },
    { base: 'dz', letters: '\u01F3\u01C6' },
    {
      base: 'e',
      letters:
        'e\u24D4\uFF45\xE8\xE9\xEA\u1EC1\u1EBF\u1EC5\u1EC3\u1EBD\u0113\u1E15\u1E17\u0115\u0117\xEB\u1EBB\u011B\u0205\u0207\u1EB9\u1EC7\u0229\u1E1D\u0119\u1E19\u1E1B\u0247\u025B\u01DD',
    },
    { base: 'f', letters: 'f\u24D5\uFF46\u1E1F\u0192\uA77C' },
    {
      base: 'g',
      letters:
        'g\u24D6\uFF47\u01F5\u011D\u1E21\u011F\u0121\u01E7\u0123\u01E5\u0260\uA7A1\u1D79\uA77F',
    },
    {
      base: 'h',
      letters:
        'h\u24D7\uFF48\u0125\u1E23\u1E27\u021F\u1E25\u1E29\u1E2B\u1E96\u0127\u2C68\u2C76\u0265',
    },
    { base: 'hv', letters: '\u0195' },
    {
      base: 'i',
      letters:
        'i\u24D8\uFF49\xEC\xED\xEE\u0129\u012B\u012D\xEF\u1E2F\u1EC9\u01D0\u0209\u020B\u1ECB\u012F\u1E2D\u0268\u0131',
    },
    { base: 'j', letters: 'j\u24D9\uFF4A\u0135\u01F0\u0249' },
    {
      base: 'k',
      letters:
        'k\u24DA\uFF4B\u1E31\u01E9\u1E33\u0137\u1E35\u0199\u2C6A\uA741\uA743\uA745\uA7A3',
    },
    {
      base: 'l',
      letters:
        'l\u24DB\uFF4C\u0140\u013A\u013E\u1E37\u1E39\u013C\u1E3D\u1E3B\u017F\u0142\u019A\u026B\u2C61\uA749\uA781\uA747',
    },
    { base: 'lj', letters: '\u01C9' },
    { base: 'm', letters: 'm\u24DC\uFF4D\u1E3F\u1E41\u1E43\u0271\u026F' },
    {
      base: 'n',
      letters:
        'n\u24DD\uFF4E\u01F9\u0144\xF1\u1E45\u0148\u1E47\u0146\u1E4B\u1E49\u019E\u0272\u0149\uA791\uA7A5',
    },
    { base: 'nj', letters: '\u01CC' },
    {
      base: 'o',
      letters:
        'o\u24DE\uFF4F\xF2\xF3\xF4\u1ED3\u1ED1\u1ED7\u1ED5\xF5\u1E4D\u022D\u1E4F\u014D\u1E51\u1E53\u014F\u022F\u0231\xF6\u022B\u1ECF\u0151\u01D2\u020D\u020F\u01A1\u1EDD\u1EDB\u1EE1\u1EDF\u1EE3\u1ECD\u1ED9\u01EB\u01ED\xF8\u01FF\u0254\uA74B\uA74D\u0275',
    },
    { base: 'oi', letters: '\u01A3' },
    { base: 'ou', letters: '\u0223' },
    { base: 'oo', letters: '\uA74F' },
    {
      base: 'p',
      letters: 'p\u24DF\uFF50\u1E55\u1E57\u01A5\u1D7D\uA751\uA753\uA755',
    },
    { base: 'q', letters: 'q\u24E0\uFF51\u024B\uA757\uA759' },
    {
      base: 'r',
      letters:
        'r\u24E1\uFF52\u0155\u1E59\u0159\u0211\u0213\u1E5B\u1E5D\u0157\u1E5F\u024D\u027D\uA75B\uA7A7\uA783',
    },
    {
      base: 's',
      letters:
        's\u24E2\uFF53\xDF\u015B\u1E65\u015D\u1E61\u0161\u1E67\u1E63\u1E69\u0219\u015F\u023F\uA7A9\uA785\u1E9B',
    },
    {
      base: 't',
      letters:
        't\u24E3\uFF54\u1E6B\u1E97\u0165\u1E6D\u021B\u0163\u1E71\u1E6F\u0167\u01AD\u0288\u2C66\uA787',
    },
    { base: 'tz', letters: '\uA729' },
    {
      base: 'u',
      letters:
        'u\u24E4\uFF55\xF9\xFA\xFB\u0169\u1E79\u016B\u1E7B\u016D\xFC\u01DC\u01D8\u01D6\u01DA\u1EE7\u016F\u0171\u01D4\u0215\u0217\u01B0\u1EEB\u1EE9\u1EEF\u1EED\u1EF1\u1EE5\u1E73\u0173\u1E77\u1E75\u0289',
    },
    { base: 'v', letters: 'v\u24E5\uFF56\u1E7D\u1E7F\u028B\uA75F\u028C' },
    { base: 'vy', letters: '\uA761' },
    {
      base: 'w',
      letters: 'w\u24E6\uFF57\u1E81\u1E83\u0175\u1E87\u1E85\u1E98\u1E89\u2C73',
    },
    { base: 'x', letters: 'x\u24E7\uFF58\u1E8B\u1E8D' },
    {
      base: 'y',
      letters:
        'y\u24E8\uFF59\u1EF3\xFD\u0177\u1EF9\u0233\u1E8F\xFF\u1EF7\u1E99\u1EF5\u01B4\u024F\u1EFF',
    },
    {
      base: 'z',
      letters:
        'z\u24E9\uFF5A\u017A\u1E91\u017C\u017E\u1E93\u1E95\u01B6\u0225\u0240\u2C6C\uA763',
    },
  ];
  var Rb = new RegExp(
    '[' +
      Bo.map(function (i) {
        return i.letters;
      }).join('') +
      ']',
    'g'
  );
  var Ac = {};
  for (var No = 0; No < Bo.length; No++) {
    var Io = Bo[No];
    for (var Ho = 0; Ho < Io.letters.length; Ho++) {
      Ac[Io.letters[Ho]] = Io.base;
    }
  }
  var Oc = function (t) {
    return t.replace(Rb, function (e) {
      return Ac[e];
    });
  };
  var Mb = Cb(Oc);
  var Dc = function (t) {
    return t.replace(/^\s+|\s+$/g, '');
  };
  var Fb = function (t) {
    return ''.concat(t.label, ' ').concat(t.value);
  };
  var Lb = function (t) {
    return function (e, n) {
      var r = Ne(
        {
          ignoreCase: true,
          ignoreAccents: true,
          stringify: Fb,
          trim: true,
          matchFrom: 'any',
        },
        t
      );
      var o = r.ignoreCase;
      var s = r.ignoreAccents;
      var a = r.stringify;
      var l = r.trim;
      var u = r.matchFrom;
      var c = l ? Dc(n) : n;
      var h = l ? Dc(a(e)) : a(e);
      if (o) {
        c = c.toLowerCase();
        h = h.toLowerCase();
      }
      if (s) {
        c = Mb(c);
        h = Oc(h);
      }
      if (u === 'start') {
        return h.substr(0, c.length) === c;
      } else {
        return h.indexOf(c) > -1;
      }
    };
  };
  var Bb = function (t) {
    t.preventDefault();
    t.stopPropagation();
  };
  var Rc = ['boxSizing', 'height', 'overflow', 'paddingRight', 'position'];
  var Mc = {
    boxSizing: 'border-box',
    overflow: 'hidden',
    position: 'relative',
    height: '100%',
  };
  var Nc =
    typeof window != 'undefined' &&
    !!window.document &&
    !!window.document.createElement;
  var zn = 0;
  var fn = { capture: false, passive: false };
  var Hb = function () {
    return document.activeElement && document.activeElement.blur();
  };
  var zb = {
    name: '1kfdb0e',
    styles: 'position:fixed;left:0;bottom:0;right:0;top:0',
  };
  var Ub = function (t) {
    return t.label;
  };
  var Wb = function (t) {
    return t.label;
  };
  var jb = function (t) {
    return t.value;
  };
  var Gb = function (t) {
    return !!t.isDisabled;
  };
  var qb = {
    clearIndicator: Gw,
    container: Bw,
    control: Jw,
    dropdownIndicator: Ww,
    group: $w,
    groupHeading: tb,
    indicatorsContainer: zw,
    indicatorSeparator: Xw,
    input: ib,
    loadingIndicator: Kw,
    loadingMessage: Fw,
    menu: Aw,
    menuList: Dw,
    menuPortal: Lw,
    multiValue: sb,
    multiValueLabel: ab,
    multiValueRemove: lb,
    noOptionsMessage: Mw,
    option: db,
    placeholder: pb,
    singleValue: gb,
    valueContainer: Iw,
  };
  var Xb = {
    primary: '#2684FF',
    primary75: '#4C9AFF',
    primary50: '#B2D4FF',
    primary25: '#DEEBFF',
    danger: '#DE350B',
    dangerLight: '#FFBDAD',
    neutral0: 'hsl(0, 0%, 100%)',
    neutral5: 'hsl(0, 0%, 95%)',
    neutral10: 'hsl(0, 0%, 90%)',
    neutral20: 'hsl(0, 0%, 80%)',
    neutral30: 'hsl(0, 0%, 70%)',
    neutral40: 'hsl(0, 0%, 60%)',
    neutral50: 'hsl(0, 0%, 50%)',
    neutral60: 'hsl(0, 0%, 40%)',
    neutral70: 'hsl(0, 0%, 30%)',
    neutral80: 'hsl(0, 0%, 20%)',
    neutral90: 'hsl(0, 0%, 10%)',
  };
  var Yb = 4;
  var Ic = 4;
  var Zb = 38;
  var Kb = Ic * 2;
  var Jb = { baseUnit: Ic, controlHeight: Zb, menuGutter: Kb };
  var zo = { borderRadius: Yb, colors: Xb, spacing: Jb };
  var Qb = {
    'aria-live': 'polite',
    backspaceRemovesValue: true,
    blurInputOnSelect: dc(),
    captureMenuScroll: !dc(),
    closeMenuOnSelect: true,
    closeMenuOnScroll: false,
    components: {},
    controlShouldRenderValue: true,
    escapeClearsValue: false,
    filterOption: Lb(),
    formatGroupLabel: Ub,
    getOptionLabel: Wb,
    getOptionValue: jb,
    isDisabled: false,
    isLoading: false,
    isMulti: false,
    isRtl: false,
    isSearchable: true,
    isOptionDisabled: Gb,
    loadingMessage: function () {
      return 'Loading...';
    },
    maxMenuHeight: 300,
    minMenuHeight: 140,
    menuIsOpen: false,
    menuPlacement: 'bottom',
    menuPosition: 'absolute',
    menuShouldBlockScroll: false,
    menuShouldScrollIntoView: !_w(),
    noOptionsMessage: function () {
      return 'No options';
    },
    openMenuOnFocus: false,
    openMenuOnClick: true,
    options: [],
    pageSize: 5,
    placeholder: 'Select...',
    screenReaderStatus: function (t) {
      var e = t.count;
      return ''.concat(e, ' result').concat(e !== 1 ? 's' : '', ' available');
    },
    styles: {},
    tabIndex: '0',
    tabSelectsValue: true,
  };
  var Wc = function (t, e) {
    return t.getOptionLabel(e);
  };
  var Ji = function (t, e) {
    return t.getOptionValue(e);
  };
  var Xc = function (t) {
    var e = t.hideSelectedOptions;
    var n = t.isMulti;
    if (e === void 0) {
      return n;
    } else {
      return e;
    }
  };
  var nS = 1;
  var Yc = (function () {
    function e(n) {
      Vi(this, e);
      var r = t.call(this, n);
      r.state = {
        ariaSelection: null,
        focusedOption: null,
        focusedValue: null,
        inputIsHidden: false,
        isFocused: false,
        selectValue: [],
        clearFocusValueOnUpdate: false,
        inputIsHiddenAfterUpdate: void 0,
        prevProps: void 0,
      };
      r.blockOptionHover = false;
      r.isComposing = false;
      r.commonProps = void 0;
      r.initialTouchX = 0;
      r.initialTouchY = 0;
      r.instancePrefix = '';
      r.openAfterFocus = false;
      r.scrollToFocusedOptionOnUpdate = false;
      r.userIsDragging = void 0;
      r.controlRef = null;
      r.getControlRef = function (o) {
        r.controlRef = o;
      };
      r.focusedOptionRef = null;
      r.getFocusedOptionRef = function (o) {
        r.focusedOptionRef = o;
      };
      r.menuListRef = null;
      r.getMenuListRef = function (o) {
        r.menuListRef = o;
      };
      r.inputRef = null;
      r.getInputRef = function (o) {
        r.inputRef = o;
      };
      r.focus = r.focusInput;
      r.blur = r.blurInput;
      r.onChange = function (o, s) {
        var a = r.props;
        var l = a.onChange;
        var u = a.name;
        s.name = u;
        r.ariaOnChange(o, s);
        l(o, s);
      };
      r.setValue = function (o) {
        var s =
          arguments.length > 1 && arguments[1] !== void 0
            ? arguments[1]
            : 'set-value';
        var a = arguments.length > 2 ? arguments[2] : void 0;
        var l = r.props;
        var u = l.closeMenuOnSelect;
        var c = l.isMulti;
        r.onInputChange('', { action: 'set-value' });
        if (u) {
          r.setState({ inputIsHiddenAfterUpdate: !c });
          r.onMenuClose();
        }
        r.setState({ clearFocusValueOnUpdate: true });
        r.onChange(o, { action: s, option: a });
      };
      r.selectOption = function (o) {
        var s = r.props;
        var a = s.blurInputOnSelect;
        var l = s.isMulti;
        var u = s.name;
        var c = r.state.selectValue;
        var h = l && r.isOptionSelected(o, c);
        var d = r.isOptionDisabled(o, c);
        if (h) {
          var g = r.getOptionValue(o);
          r.setValue(
            c.filter(function (y) {
              return r.getOptionValue(y) !== g;
            }),
            'deselect-option',
            o
          );
        } else if (!d) {
          if (l) {
            r.setValue([].concat(Cc(c), [o]), 'select-option', o);
          } else {
            r.setValue(o, 'select-option');
          }
        } else {
          r.ariaOnChange(o, { action: 'select-option', name: u });
          return;
        }
        if (a) {
          r.blurInput();
        }
      };
      r.removeValue = function (o) {
        var s = r.props.isMulti;
        var a = r.state.selectValue;
        var l = r.getOptionValue(o);
        var u = a.filter(function (h) {
          return r.getOptionValue(h) !== l;
        });
        var c = s ? u : u[0] || null;
        r.onChange(c, { action: 'remove-value', removedValue: o });
        r.focusInput();
      };
      r.clearValue = function () {
        var o = r.state.selectValue;
        r.onChange(r.props.isMulti ? [] : null, {
          action: 'clear',
          removedValues: o,
        });
      };
      r.popValue = function () {
        var o = r.props.isMulti;
        var s = r.state.selectValue;
        var a = s[s.length - 1];
        var l = s.slice(0, s.length - 1);
        var u = o ? l : l[0] || null;
        r.onChange(u, { action: 'pop-value', removedValue: a });
      };
      r.getValue = function () {
        return r.state.selectValue;
      };
      r.cx = function () {
        var o = arguments.length;
        var s = new Array(o);
        for (var a = 0; a < o; a++) {
          s[a] = arguments[a];
        }
        return yw.apply(void 0, [r.props.classNamePrefix].concat(s));
      };
      r.getOptionLabel = function (o) {
        return Wc(r.props, o);
      };
      r.getOptionValue = function (o) {
        return Ji(r.props, o);
      };
      r.getStyles = function (o, s) {
        var a = qb[o](s);
        a.boxSizing = 'border-box';
        var l = r.props.styles[o];
        if (l) {
          return l(a, s);
        } else {
          return a;
        }
      };
      r.getElementId = function (o) {
        return ''.concat(r.instancePrefix, '-').concat(o);
      };
      r.getComponents = function () {
        return wb(r.props);
      };
      r.buildCategorizedOptions = function () {
        return zc(r.props, r.state.selectValue);
      };
      r.getCategorizedOptions = function () {
        if (r.props.menuIsOpen) {
          return r.buildCategorizedOptions();
        } else {
          return [];
        }
      };
      r.buildFocusableOptions = function () {
        return Vc(r.buildCategorizedOptions());
      };
      r.getFocusableOptions = function () {
        if (r.props.menuIsOpen) {
          return r.buildFocusableOptions();
        } else {
          return [];
        }
      };
      r.ariaOnChange = function (o, s) {
        r.setState({ ariaSelection: Ne({ value: o }, s) });
      };
      r.onMenuMouseDown = function (o) {
        if (o.button === 0) {
          o.stopPropagation();
          o.preventDefault();
          r.focusInput();
        }
      };
      r.onMenuMouseMove = function (o) {
        r.blockOptionHover = false;
      };
      r.onControlMouseDown = function (o) {
        var s = r.props.openMenuOnClick;
        if (r.state.isFocused) {
          if (r.props.menuIsOpen) {
            if (
              o.target.tagName !== 'INPUT' &&
              o.target.tagName !== 'TEXTAREA'
            ) {
              r.onMenuClose();
            }
          } else if (s) {
            r.openMenu('first');
          }
        } else {
          if (s) {
            r.openAfterFocus = true;
          }
          r.focusInput();
        }
        if (o.target.tagName !== 'INPUT' && o.target.tagName !== 'TEXTAREA') {
          o.preventDefault();
        }
      };
      r.onDropdownIndicatorMouseDown = function (o) {
        if (
          (!o || o.type !== 'mousedown' || o.button === 0) &&
          !r.props.isDisabled
        ) {
          var s = r.props;
          var a = s.isMulti;
          var l = s.menuIsOpen;
          r.focusInput();
          if (l) {
            r.setState({ inputIsHiddenAfterUpdate: !a });
            r.onMenuClose();
          } else {
            r.openMenu('first');
          }
          o.preventDefault();
          o.stopPropagation();
        }
      };
      r.onClearIndicatorMouseDown = function (o) {
        if (!o || o.type !== 'mousedown' || o.button === 0) {
          r.clearValue();
          o.stopPropagation();
          r.openAfterFocus = false;
          if (o.type === 'touchend') {
            r.focusInput();
          } else {
            setTimeout(function () {
              return r.focusInput();
            });
          }
        }
      };
      r.onScroll = function (o) {
        if (typeof r.props.closeMenuOnScroll == 'boolean') {
          if (o.target instanceof HTMLElement && Ro(o.target)) {
            r.props.onMenuClose();
          }
        } else if (
          typeof r.props.closeMenuOnScroll == 'function' &&
          r.props.closeMenuOnScroll(o)
        ) {
          r.props.onMenuClose();
        }
      };
      r.onCompositionStart = function () {
        r.isComposing = true;
      };
      r.onCompositionEnd = function () {
        r.isComposing = false;
      };
      r.onTouchStart = function (o) {
        var s = o.touches;
        var a = s && s.item(0);
        if (a) {
          r.initialTouchX = a.clientX;
          r.initialTouchY = a.clientY;
          r.userIsDragging = false;
        }
      };
      r.onTouchMove = function (o) {
        var s = o.touches;
        var a = s && s.item(0);
        if (a) {
          var l = Math.abs(a.clientX - r.initialTouchX);
          var u = Math.abs(a.clientY - r.initialTouchY);
          var c = 5;
          r.userIsDragging = l > c || u > c;
        }
      };
      r.onTouchEnd = function (o) {
        if (!r.userIsDragging) {
          if (
            r.controlRef &&
            !r.controlRef.contains(o.target) &&
            r.menuListRef &&
            !r.menuListRef.contains(o.target)
          ) {
            r.blurInput();
          }
          r.initialTouchX = 0;
          r.initialTouchY = 0;
        }
      };
      r.onControlTouchEnd = function (o) {
        if (!r.userIsDragging) {
          r.onControlMouseDown(o);
        }
      };
      r.onClearIndicatorTouchEnd = function (o) {
        if (!r.userIsDragging) {
          r.onClearIndicatorMouseDown(o);
        }
      };
      r.onDropdownIndicatorTouchEnd = function (o) {
        if (!r.userIsDragging) {
          r.onDropdownIndicatorMouseDown(o);
        }
      };
      r.handleInputChange = function (o) {
        var s = o.currentTarget.value;
        r.setState({ inputIsHiddenAfterUpdate: false });
        r.onInputChange(s, { action: 'input-change' });
        if (!r.props.menuIsOpen) {
          r.onMenuOpen();
        }
      };
      r.onInputFocus = function (o) {
        if (r.props.onFocus) {
          r.props.onFocus(o);
        }
        r.setState({ inputIsHiddenAfterUpdate: false, isFocused: true });
        if (r.openAfterFocus || r.props.openMenuOnFocus) {
          r.openMenu('first');
        }
        r.openAfterFocus = false;
      };
      r.onInputBlur = function (o) {
        if (r.menuListRef && r.menuListRef.contains(document.activeElement)) {
          r.inputRef.focus();
          return;
        }
        if (r.props.onBlur) {
          r.props.onBlur(o);
        }
        r.onInputChange('', { action: 'input-blur' });
        r.onMenuClose();
        r.setState({ focusedValue: null, isFocused: false });
      };
      r.onOptionHover = function (o) {
        if (!r.blockOptionHover && r.state.focusedOption !== o) {
          r.setState({ focusedOption: o });
        }
      };
      r.shouldHideSelectedOptions = function () {
        return Xc(r.props);
      };
      r.onKeyDown = function (o) {
        var s = r.props;
        var a = s.isMulti;
        var l = s.backspaceRemovesValue;
        var u = s.escapeClearsValue;
        var c = s.inputValue;
        var h = s.isClearable;
        var d = s.isDisabled;
        var g = s.menuIsOpen;
        var y = s.onKeyDown;
        var x = s.tabSelectsValue;
        var b = s.openMenuOnFocus;
        var T = r.state;
        var f = T.focusedOption;
        var E = T.focusedValue;
        var A = T.selectValue;
        if (!d && (typeof y != 'function' || !(y(o), o.defaultPrevented))) {
          switch (((r.blockOptionHover = true), o.key)) {
            case 'ArrowLeft':
              if (!a || c) {
                return;
              }
              r.focusValue('previous');
              break;
            case 'ArrowRight':
              if (!a || c) {
                return;
              }
              r.focusValue('next');
              break;
            case 'Delete':
            case 'Backspace':
              if (c) {
                return;
              }
              if (E) {
                r.removeValue(E);
              } else {
                if (!l) {
                  return;
                }
                if (a) {
                  r.popValue();
                } else if (h) {
                  r.clearValue();
                }
              }
              break;
            case 'Tab':
              if (
                r.isComposing ||
                o.shiftKey ||
                !g ||
                !x ||
                !f ||
                (b && r.isOptionSelected(f, A))
              ) {
                return;
              }
              r.selectOption(f);
              break;
            case 'Enter':
              if (o.keyCode === 229) {
                break;
              }
              if (g) {
                if (!f || r.isComposing) {
                  return;
                }
                r.selectOption(f);
                break;
              }
              return;
            case 'Escape':
              if (g) {
                r.setState({ inputIsHiddenAfterUpdate: false });
                r.onInputChange('', { action: 'menu-close' });
                r.onMenuClose();
              } else if (h && u) {
                r.clearValue();
              }
              break;
            case ' ':
              if (c) {
                return;
              }
              if (!g) {
                r.openMenu('first');
                break;
              }
              if (!f) {
                return;
              }
              r.selectOption(f);
              break;
            case 'ArrowUp':
              if (g) {
                r.focusOption('up');
              } else {
                r.openMenu('last');
              }
              break;
            case 'ArrowDown':
              if (g) {
                r.focusOption('down');
              } else {
                r.openMenu('first');
              }
              break;
            case 'PageUp':
              if (!g) {
                return;
              }
              r.focusOption('pageup');
              break;
            case 'PageDown':
              if (!g) {
                return;
              }
              r.focusOption('pagedown');
              break;
            case 'Home':
              if (!g) {
                return;
              }
              r.focusOption('first');
              break;
            case 'End':
              if (!g) {
                return;
              }
              r.focusOption('last');
              break;
            default:
              return;
          }
          o.preventDefault();
        }
      };
      r.instancePrefix = 'react-select-' + (r.props.instanceId || ++nS);
      r.state.selectValue = uc(n.value);
      return r;
    }
    var i = Oe;
    ji(e, i);
    var t = qi(e);
    Ui(
      e,
      [
        {
          key: 'componentDidMount',
          value: function () {
            this.startListeningComposition();
            this.startListeningToTouch();
            if (
              this.props.closeMenuOnScroll &&
              document &&
              document.addEventListener
            ) {
              document.addEventListener('scroll', this.onScroll, true);
            }
            if (this.props.autoFocus) {
              this.focusInput();
            }
          },
        },
        {
          key: 'componentDidUpdate',
          value: function (r) {
            var o = this.props;
            var s = o.isDisabled;
            var a = o.menuIsOpen;
            var l = this.state.isFocused;
            if ((l && !s && r.isDisabled) || (l && a && !r.menuIsOpen)) {
              this.focusInput();
            }
            if (l && s && !r.isDisabled) {
              this.setState({ isFocused: false }, this.onMenuClose);
            }
            if (
              this.menuListRef &&
              this.focusedOptionRef &&
              this.scrollToFocusedOptionOnUpdate
            ) {
              Sw(this.menuListRef, this.focusedOptionRef);
              this.scrollToFocusedOptionOnUpdate = false;
            }
          },
        },
        {
          key: 'componentWillUnmount',
          value: function () {
            this.stopListeningComposition();
            this.stopListeningToTouch();
            document.removeEventListener('scroll', this.onScroll, true);
          },
        },
        {
          key: 'onMenuOpen',
          value: function () {
            this.props.onMenuOpen();
          },
        },
        {
          key: 'onMenuClose',
          value: function () {
            this.onInputChange('', { action: 'menu-close' });
            this.props.onMenuClose();
          },
        },
        {
          key: 'onInputChange',
          value: function (r, o) {
            this.props.onInputChange(r, o);
          },
        },
        {
          key: 'focusInput',
          value: function () {
            if (this.inputRef) {
              this.inputRef.focus();
            }
          },
        },
        {
          key: 'blurInput',
          value: function () {
            if (this.inputRef) {
              this.inputRef.blur();
            }
          },
        },
        {
          key: 'openMenu',
          value: function (r) {
            var o = this;
            var s = this.state;
            var a = s.selectValue;
            var l = s.isFocused;
            var u = this.buildFocusableOptions();
            var c = r === 'first' ? 0 : u.length - 1;
            if (!this.props.isMulti) {
              var h = u.indexOf(a[0]);
              if (h > -1) {
                c = h;
              }
            }
            this.scrollToFocusedOptionOnUpdate = !l || !this.menuListRef;
            this.setState(
              {
                inputIsHiddenAfterUpdate: false,
                focusedValue: null,
                focusedOption: u[c],
              },
              function () {
                return o.onMenuOpen();
              }
            );
          },
        },
        {
          key: 'focusValue',
          value: function (r) {
            var o = this.state;
            var s = o.selectValue;
            var a = o.focusedValue;
            if (this.props.isMulti) {
              this.setState({ focusedOption: null });
              var l = s.indexOf(a);
              if (!a) {
                l = -1;
              }
              var u = s.length - 1;
              var c = -1;
              if (s.length) {
                switch (r) {
                  case 'previous':
                    if (l === 0) {
                      c = 0;
                    } else if (l === -1) {
                      c = u;
                    } else {
                      c = l - 1;
                    }
                    break;
                  case 'next':
                    if (l > -1 && l < u) {
                      c = l + 1;
                    }
                    break;
                }
                this.setState({ inputIsHidden: c !== -1, focusedValue: s[c] });
              }
            }
          },
        },
        {
          key: 'focusOption',
          value: function () {
            var r =
              arguments.length > 0 && arguments[0] !== void 0
                ? arguments[0]
                : 'first';
            var o = this.props.pageSize;
            var s = this.state.focusedOption;
            var a = this.getFocusableOptions();
            if (a.length) {
              var l = 0;
              var u = a.indexOf(s);
              if (!s) {
                u = -1;
              }
              if (r === 'up') {
                l = u > 0 ? u - 1 : a.length - 1;
              } else if (r === 'down') {
                l = (u + 1) % a.length;
              } else if (r === 'pageup') {
                l = u - o;
                if (l < 0) {
                  l = 0;
                }
              } else if (r === 'pagedown') {
                l = u + o;
                if (l > a.length - 1) {
                  l = a.length - 1;
                }
              } else if (r === 'last') {
                l = a.length - 1;
              }
              this.scrollToFocusedOptionOnUpdate = true;
              this.setState({ focusedOption: a[l], focusedValue: null });
            }
          },
        },
        {
          key: 'getTheme',
          value: function () {
            if (this.props.theme) {
              if (typeof this.props.theme == 'function') {
                return this.props.theme(zo);
              } else {
                return Ne(Ne({}, zo), this.props.theme);
              }
            } else {
              return zo;
            }
          },
        },
        {
          key: 'getCommonProps',
          value: function () {
            var r = this.clearValue;
            var o = this.cx;
            var s = this.getStyles;
            var a = this.getValue;
            var l = this.selectOption;
            var u = this.setValue;
            var c = this.props;
            var h = c.isMulti;
            var d = c.isRtl;
            var g = c.options;
            var y = this.hasValue();
            return {
              clearValue: r,
              cx: o,
              getStyles: s,
              getValue: a,
              hasValue: y,
              isMulti: h,
              isRtl: d,
              options: g,
              selectOption: l,
              selectProps: c,
              setValue: u,
              theme: this.getTheme(),
            };
          },
        },
        {
          key: 'hasValue',
          value: function () {
            var r = this.state.selectValue;
            return r.length > 0;
          },
        },
        {
          key: 'hasOptions',
          value: function () {
            return !!this.getFocusableOptions().length;
          },
        },
        {
          key: 'isClearable',
          value: function () {
            var r = this.props;
            var o = r.isClearable;
            var s = r.isMulti;
            if (o === void 0) {
              return s;
            } else {
              return o;
            }
          },
        },
        {
          key: 'isOptionDisabled',
          value: function (r, o) {
            return jc(this.props, r, o);
          },
        },
        {
          key: 'isOptionSelected',
          value: function (r, o) {
            return Gc(this.props, r, o);
          },
        },
        {
          key: 'filterOption',
          value: function (r, o) {
            return qc(this.props, r, o);
          },
        },
        {
          key: 'formatOptionLabel',
          value: function (r, o) {
            if (typeof this.props.formatOptionLabel == 'function') {
              var s = this.props.inputValue;
              var a = this.state.selectValue;
              return this.props.formatOptionLabel(r, {
                context: o,
                inputValue: s,
                selectValue: a,
              });
            } else {
              return this.getOptionLabel(r);
            }
          },
        },
        {
          key: 'formatGroupLabel',
          value: function (r) {
            return this.props.formatGroupLabel(r);
          },
        },
        {
          key: 'startListeningComposition',
          value: function () {
            if (document && document.addEventListener) {
              document.addEventListener(
                'compositionstart',
                this.onCompositionStart,
                false
              );
              document.addEventListener(
                'compositionend',
                this.onCompositionEnd,
                false
              );
            }
          },
        },
        {
          key: 'stopListeningComposition',
          value: function () {
            if (document && document.removeEventListener) {
              document.removeEventListener(
                'compositionstart',
                this.onCompositionStart
              );
              document.removeEventListener(
                'compositionend',
                this.onCompositionEnd
              );
            }
          },
        },
        {
          key: 'startListeningToTouch',
          value: function () {
            if (document && document.addEventListener) {
              document.addEventListener('touchstart', this.onTouchStart, false);
              document.addEventListener('touchmove', this.onTouchMove, false);
              document.addEventListener('touchend', this.onTouchEnd, false);
            }
          },
        },
        {
          key: 'stopListeningToTouch',
          value: function () {
            if (document && document.removeEventListener) {
              document.removeEventListener('touchstart', this.onTouchStart);
              document.removeEventListener('touchmove', this.onTouchMove);
              document.removeEventListener('touchend', this.onTouchEnd);
            }
          },
        },
        {
          key: 'renderInput',
          value: function () {
            var r = this.props;
            var o = r.isDisabled;
            var s = r.isSearchable;
            var a = r.inputId;
            var l = r.inputValue;
            var u = r.tabIndex;
            var c = r.form;
            var h = this.getComponents();
            var d = h.Input;
            var g = this.state.inputIsHidden;
            var y = this.commonProps;
            var x = a || this.getElementId('input');
            var b = {
              'aria-autocomplete': 'list',
              'aria-label': this.props['aria-label'],
              'aria-labelledby': this.props['aria-labelledby'],
            };
            if (s) {
              return L.createElement(
                d,
                ne(
                  {},
                  y,
                  {
                    autoCapitalize: 'none',
                    autoComplete: 'off',
                    autoCorrect: 'off',
                    id: x,
                    innerRef: this.getInputRef,
                    isDisabled: o,
                    isHidden: g,
                    onBlur: this.onInputBlur,
                    onChange: this.handleInputChange,
                    onFocus: this.onInputFocus,
                    spellCheck: 'false',
                    tabIndex: u,
                    form: c,
                    type: 'text',
                    value: l,
                  },
                  b
                )
              );
            } else {
              return L.createElement(
                kb,
                ne(
                  {
                    id: x,
                    innerRef: this.getInputRef,
                    onBlur: this.onInputBlur,
                    onChange: Xi,
                    onFocus: this.onInputFocus,
                    readOnly: true,
                    disabled: o,
                    tabIndex: u,
                    form: c,
                    value: '',
                  },
                  b
                )
              );
            }
          },
        },
        {
          key: 'renderPlaceholderOrValue',
          value: function () {
            var r = this;
            var o = this.getComponents();
            var s = o.MultiValue;
            var a = o.MultiValueContainer;
            var l = o.MultiValueLabel;
            var u = o.MultiValueRemove;
            var c = o.SingleValue;
            var h = o.Placeholder;
            var d = this.commonProps;
            var g = this.props;
            var y = g.controlShouldRenderValue;
            var x = g.isDisabled;
            var b = g.isMulti;
            var T = g.inputValue;
            var f = g.placeholder;
            var E = this.state;
            var A = E.selectValue;
            var C = E.focusedValue;
            var O = E.isFocused;
            if (!this.hasValue() || !y) {
              if (T) {
                return null;
              } else {
                return L.createElement(
                  h,
                  ne({}, d, {
                    key: 'placeholder',
                    isDisabled: x,
                    isFocused: O,
                  }),
                  f
                );
              }
            }
            if (b) {
              var D = A.map(function (B, Z) {
                var Y = B === C;
                return L.createElement(
                  s,
                  ne({}, d, {
                    components: { Container: a, Label: l, Remove: u },
                    isFocused: Y,
                    isDisabled: x,
                    key: ''.concat(r.getOptionValue(B)).concat(Z),
                    index: Z,
                    removeProps: {
                      onClick: function () {
                        return r.removeValue(B);
                      },
                      onTouchEnd: function () {
                        return r.removeValue(B);
                      },
                      onMouseDown: function (K) {
                        K.preventDefault();
                        K.stopPropagation();
                      },
                    },
                    data: B,
                  }),
                  r.formatOptionLabel(B, 'value')
                );
              });
              return D;
            }
            if (T) {
              return null;
            }
            var N = A[0];
            return L.createElement(
              c,
              ne({}, d, { data: N, isDisabled: x }),
              this.formatOptionLabel(N, 'value')
            );
          },
        },
        {
          key: 'renderClearIndicator',
          value: function () {
            var r = this.getComponents();
            var o = r.ClearIndicator;
            var s = this.commonProps;
            var a = this.props;
            var l = a.isDisabled;
            var u = a.isLoading;
            var c = this.state.isFocused;
            if (!this.isClearable() || !o || l || !this.hasValue() || u) {
              return null;
            }
            var h = {
              onMouseDown: this.onClearIndicatorMouseDown,
              onTouchEnd: this.onClearIndicatorTouchEnd,
              'aria-hidden': 'true',
            };
            return L.createElement(
              o,
              ne({}, s, { innerProps: h, isFocused: c })
            );
          },
        },
        {
          key: 'renderLoadingIndicator',
          value: function () {
            var r = this.getComponents();
            var o = r.LoadingIndicator;
            var s = this.commonProps;
            var a = this.props;
            var l = a.isDisabled;
            var u = a.isLoading;
            var c = this.state.isFocused;
            if (!o || !u) {
              return null;
            }
            var h = { 'aria-hidden': 'true' };
            return L.createElement(
              o,
              ne({}, s, { innerProps: h, isDisabled: l, isFocused: c })
            );
          },
        },
        {
          key: 'renderIndicatorSeparator',
          value: function () {
            var r = this.getComponents();
            var o = r.DropdownIndicator;
            var s = r.IndicatorSeparator;
            if (!o || !s) {
              return null;
            }
            var a = this.commonProps;
            var l = this.props.isDisabled;
            var u = this.state.isFocused;
            return L.createElement(
              s,
              ne({}, a, { isDisabled: l, isFocused: u })
            );
          },
        },
        {
          key: 'renderDropdownIndicator',
          value: function () {
            var r = this.getComponents();
            var o = r.DropdownIndicator;
            if (!o) {
              return null;
            }
            var s = this.commonProps;
            var a = this.props.isDisabled;
            var l = this.state.isFocused;
            var u = {
              onMouseDown: this.onDropdownIndicatorMouseDown,
              onTouchEnd: this.onDropdownIndicatorTouchEnd,
              'aria-hidden': 'true',
            };
            return L.createElement(
              o,
              ne({}, s, { innerProps: u, isDisabled: a, isFocused: l })
            );
          },
        },
        {
          key: 'renderMenu',
          value: function () {
            var r = this;
            var o = this.getComponents();
            var s = o.Group;
            var a = o.GroupHeading;
            var l = o.Menu;
            var u = o.MenuList;
            var c = o.MenuPortal;
            var h = o.LoadingMessage;
            var d = o.NoOptionsMessage;
            var g = o.Option;
            var y = this.commonProps;
            var x = this.state.focusedOption;
            var b = this.props;
            var T = b.captureMenuScroll;
            var f = b.inputValue;
            var E = b.isLoading;
            var A = b.loadingMessage;
            var C = b.minMenuHeight;
            var O = b.maxMenuHeight;
            var D = b.menuIsOpen;
            var N = b.menuPlacement;
            var B = b.menuPosition;
            var Z = b.menuPortalTarget;
            var Y = b.menuShouldBlockScroll;
            var U = b.menuShouldScrollIntoView;
            var K = b.noOptionsMessage;
            var Q = b.onMenuScrollToTop;
            var le = b.onMenuScrollToBottom;
            if (!D) {
              return null;
            }
            var re = function (V, z) {
              var j = V.type;
              var G = V.data;
              var $ = V.isDisabled;
              var ae = V.isSelected;
              var Se = V.label;
              var ge = V.value;
              var tt = x === G;
              var nt = $
                ? void 0
                : function () {
                    return r.onOptionHover(G);
                  };
              var it = $
                ? void 0
                : function () {
                    return r.selectOption(G);
                  };
              var p = ''.concat(r.getElementId('option'), '-').concat(z);
              var _ = {
                id: p,
                onClick: it,
                onMouseMove: nt,
                onMouseOver: nt,
                tabIndex: -1,
              };
              return L.createElement(
                g,
                ne({}, y, {
                  innerProps: _,
                  data: G,
                  isDisabled: $,
                  isSelected: ae,
                  key: p,
                  label: Se,
                  type: j,
                  value: ge,
                  isFocused: tt,
                  innerRef: tt ? r.getFocusedOptionRef : void 0,
                }),
                r.formatOptionLabel(V.data, 'menu')
              );
            };
            var se;
            if (this.hasOptions()) {
              se = this.getCategorizedOptions().map(function (F) {
                if (F.type === 'group') {
                  var V = F.data;
                  var z = F.options;
                  var j = F.index;
                  var G = ''.concat(r.getElementId('group'), '-').concat(j);
                  var $ = ''.concat(G, '-heading');
                  return L.createElement(
                    s,
                    ne({}, y, {
                      key: G,
                      data: V,
                      options: z,
                      Heading: a,
                      headingProps: { id: $, data: F.data },
                      label: r.formatGroupLabel(F.data),
                    }),
                    F.options.map(function (ae) {
                      return re(ae, ''.concat(j, '-').concat(ae.index));
                    })
                  );
                } else if (F.type === 'option') {
                  return re(F, ''.concat(F.index));
                }
              });
            } else if (E) {
              var fe = A({ inputValue: f });
              if (fe === null) {
                return null;
              }
              se = L.createElement(h, y, fe);
            } else {
              var me = K({ inputValue: f });
              if (me === null) {
                return null;
              }
              se = L.createElement(d, y, me);
            }
            var q = {
              minMenuHeight: C,
              maxMenuHeight: O,
              menuPlacement: N,
              menuPosition: B,
              menuShouldScrollIntoView: U,
            };
            var Le = L.createElement(mc, ne({}, y, q), function (F) {
              var V = F.ref;
              var z = F.placerProps;
              var j = z.placement;
              var G = z.maxHeight;
              return L.createElement(
                l,
                ne({}, y, q, {
                  innerRef: V,
                  innerProps: {
                    onMouseDown: r.onMenuMouseDown,
                    onMouseMove: r.onMenuMouseMove,
                  },
                  isLoading: E,
                  placement: j,
                }),
                L.createElement(
                  Vb,
                  {
                    captureEnabled: T,
                    onTopArrive: Q,
                    onBottomArrive: le,
                    lockEnabled: Y,
                  },
                  function ($) {
                    return L.createElement(
                      u,
                      ne({}, y, {
                        innerRef: function (Se) {
                          r.getMenuListRef(Se);
                          $(Se);
                        },
                        isLoading: E,
                        maxHeight: G,
                        focusedOption: x,
                      }),
                      se
                    );
                  }
                )
              );
            });
            if (Z || B === 'fixed') {
              return L.createElement(
                c,
                ne({}, y, {
                  appendTo: Z,
                  controlElement: this.controlRef,
                  menuPlacement: N,
                  menuPosition: B,
                }),
                Le
              );
            } else {
              return Le;
            }
          },
        },
        {
          key: 'renderFormField',
          value: function () {
            var r = this;
            var o = this.props;
            var s = o.delimiter;
            var a = o.isDisabled;
            var l = o.isMulti;
            var u = o.name;
            var c = this.state.selectValue;
            if (!!u && !a) {
              if (l) {
                if (s) {
                  var h = c
                    .map(function (y) {
                      return r.getOptionValue(y);
                    })
                    .join(s);
                  return L.createElement('input', {
                    name: u,
                    type: 'hidden',
                    value: h,
                  });
                } else {
                  var d =
                    c.length > 0
                      ? c.map(function (y, x) {
                          return L.createElement('input', {
                            key: 'i-'.concat(x),
                            name: u,
                            type: 'hidden',
                            value: r.getOptionValue(y),
                          });
                        })
                      : L.createElement('input', { name: u, type: 'hidden' });
                  return L.createElement('div', null, d);
                }
              } else {
                var g = c[0] ? this.getOptionValue(c[0]) : '';
                return L.createElement('input', {
                  name: u,
                  type: 'hidden',
                  value: g,
                });
              }
            }
          },
        },
        {
          key: 'renderLiveRegion',
          value: function () {
            var r = this.commonProps;
            var o = this.state;
            var s = o.ariaSelection;
            var a = o.focusedOption;
            var l = o.focusedValue;
            var u = o.isFocused;
            var c = o.selectValue;
            var h = this.getFocusableOptions();
            return L.createElement(
              Db,
              ne({}, r, {
                ariaSelection: s,
                focusedOption: a,
                focusedValue: l,
                isFocused: u,
                selectValue: c,
                focusableOptions: h,
              })
            );
          },
        },
        {
          key: 'render',
          value: function () {
            var r = this.getComponents();
            var o = r.Control;
            var s = r.IndicatorsContainer;
            var a = r.SelectContainer;
            var l = r.ValueContainer;
            var u = this.props;
            var c = u.className;
            var h = u.id;
            var d = u.isDisabled;
            var g = u.menuIsOpen;
            var y = this.state.isFocused;
            var x = (this.commonProps = this.getCommonProps());
            return L.createElement(
              a,
              ne({}, x, {
                className: c,
                innerProps: { id: h, onKeyDown: this.onKeyDown },
                isDisabled: d,
                isFocused: y,
              }),
              this.renderLiveRegion(),
              L.createElement(
                o,
                ne({}, x, {
                  innerRef: this.getControlRef,
                  innerProps: {
                    onMouseDown: this.onControlMouseDown,
                    onTouchEnd: this.onControlTouchEnd,
                  },
                  isDisabled: d,
                  isFocused: y,
                  menuIsOpen: g,
                }),
                L.createElement(
                  l,
                  ne({}, x, { isDisabled: d }),
                  this.renderPlaceholderOrValue(),
                  this.renderInput()
                ),
                L.createElement(
                  s,
                  ne({}, x, { isDisabled: d }),
                  this.renderClearIndicator(),
                  this.renderLoadingIndicator(),
                  this.renderIndicatorSeparator(),
                  this.renderDropdownIndicator()
                )
              ),
              this.renderMenu(),
              this.renderFormField()
            );
          },
        },
      ],
      [
        {
          key: 'getDerivedStateFromProps',
          value: function (r, o) {
            var s = o.prevProps;
            var a = o.clearFocusValueOnUpdate;
            var l = o.inputIsHiddenAfterUpdate;
            var u = r.options;
            var c = r.value;
            var h = r.menuIsOpen;
            var d = r.inputValue;
            var g = {};
            if (
              s &&
              (c !== s.value ||
                u !== s.options ||
                h !== s.menuIsOpen ||
                d !== s.inputValue)
            ) {
              var y = uc(c);
              var x = h ? $b(r, y) : [];
              var b = a ? eS(o, y) : null;
              var T = tS(o, x);
              g = {
                selectValue: y,
                focusedOption: T,
                focusedValue: b,
                clearFocusValueOnUpdate: false,
              };
            }
            var f =
              l != null && r !== s
                ? { inputIsHidden: l, inputIsHiddenAfterUpdate: void 0 }
                : {};
            return Ne(Ne(Ne({}, g), f), {}, { prevProps: r });
          },
        },
      ]
    );
    return e;
  })();
  Yc.defaultProps = Qb;
  var iS = {
    defaultInputValue: '',
    defaultMenuIsOpen: false,
    defaultValue: null,
  };
  var rS = function (t) {
    var e;
    n = e = (function () {
      function s() {
        Vi(this, s);
        var l = arguments.length;
        var u = new Array(l);
        for (var c = 0; c < l; c++) {
          u[c] = arguments[c];
        }
        var a = o.call.apply(o, [this].concat(u));
        a.select = void 0;
        a.state = {
          inputValue:
            a.props.inputValue !== void 0
              ? a.props.inputValue
              : a.props.defaultInputValue,
          menuIsOpen:
            a.props.menuIsOpen !== void 0
              ? a.props.menuIsOpen
              : a.props.defaultMenuIsOpen,
          value:
            a.props.value !== void 0 ? a.props.value : a.props.defaultValue,
        };
        a.onChange = function (h, d) {
          a.callProp('onChange', h, d);
          a.setState({ value: h });
        };
        a.onInputChange = function (h, d) {
          var g = a.callProp('onInputChange', h, d);
          a.setState({ inputValue: g !== void 0 ? g : h });
        };
        a.onMenuOpen = function () {
          a.callProp('onMenuOpen');
          a.setState({ menuIsOpen: true });
        };
        a.onMenuClose = function () {
          a.callProp('onMenuClose');
          a.setState({ menuIsOpen: false });
        };
        return a;
      }
      var r = Oe;
      ji(s, r);
      var o = qi(s);
      Ui(s, [
        {
          key: 'focus',
          value: function () {
            this.select.focus();
          },
        },
        {
          key: 'blur',
          value: function () {
            this.select.blur();
          },
        },
        {
          key: 'getProp',
          value: function (l) {
            if (this.props[l] === void 0) {
              return this.state[l];
            } else {
              return this.props[l];
            }
          },
        },
        {
          key: 'callProp',
          value: function (l) {
            if (typeof this.props[l] == 'function') {
              var u;
              var c = arguments.length;
              var h = new Array(c > 1 ? c - 1 : 0);
              for (var d = 1; d < c; d++) {
                h[d - 1] = arguments[d];
              }
              return (u = this.props)[l].apply(u, h);
            }
          },
        },
        {
          key: 'render',
          value: function () {
            var l = this;
            var u = this.props;
            u.defaultInputValue;
            u.defaultMenuIsOpen;
            u.defaultValue;
            var c = dn(u, [
              'defaultInputValue',
              'defaultMenuIsOpen',
              'defaultValue',
            ]);
            return L.createElement(
              t,
              ne({}, c, {
                ref: function (d) {
                  l.select = d;
                },
                inputValue: this.getProp('inputValue'),
                menuIsOpen: this.getProp('menuIsOpen'),
                onChange: this.onChange,
                onInputChange: this.onInputChange,
                onMenuClose: this.onMenuClose,
                onMenuOpen: this.onMenuOpen,
                value: this.getProp('value'),
              })
            );
          },
        },
      ]);
      return s;
    })();
    e.defaultProps = iS;
    return n;
  };
  var oS = rS(Yc);
  var sS = oS;
  const Vo = [
    { value: 'assessing', label: 'Assessing' },
    { value: 'bookmarking', label: 'Bookmarking' },
    { value: 'classifying', label: 'Classifying' },
    { value: 'commenting', label: 'Commenting' },
    { value: 'describing', label: 'Describing' },
    { value: 'editing', label: 'Editing' },
    { value: 'highlighting', label: 'Highlighting' },
    { value: 'identifying', label: 'Identifying' },
    { value: 'linking', label: 'Linking' },
    { value: 'moderating', label: 'Moderating' },
    { value: 'questioning', label: 'Questioning' },
    { value: 'replying', label: 'Replying' },
    { value: 'supplementing', label: 'Transcription' },
  ];
  var Zc = (i) => {
    const t = i.content ? Vo.find((e) => e.value === i.content) : null;
    return L.createElement(
      'div',
      { className: 'r6o-purposedropdown' },
      L.createElement(sS, {
        value: t,
        onChange: i.onChange,
        options: Vo,
        isDisabled: !i.editable,
      })
    );
  };
  const aS = (i) =>
    L.createElement(
      'svg',
      {
        xmlns: 'http://www.w3.org/2000/svg',
        viewBox: '0 0 1000 940',
        width: i.width,
      },
      L.createElement('metadata', null, 'IcoFont Icons'),
      L.createElement('title', null, 'simple-down'),
      L.createElement('glyph', {
        glyphName: 'simple-down',
        unicode: '\uEAB2',
        horizAdvX: '1000',
      }),
      L.createElement('path', {
        fill: 'currentColor',
        d: 'M200 392.6l300 300 300-300-85.10000000000002-85.10000000000002-214.89999999999998 214.79999999999995-214.89999999999998-214.89999999999998-85.10000000000002 85.20000000000005z',
      })
    );
  const lS = (i) =>
    L.createElement(
      'svg',
      {
        xmlns: 'http://www.w3.org/2000/svg',
        viewBox: '180 150 700 800',
        width: i.width,
      },
      L.createElement('metadata', null, 'IcoFont Icons'),
      L.createElement('title', null, 'close'),
      L.createElement('glyph', {
        glyphName: 'close',
        unicode: '\uEEE4',
        horizAdvX: '1000',
      }),
      L.createElement('path', {
        fill: 'currentColor',
        d: 'M709.8 206.6c-64.39999999999998 65.50000000000003-128.89999999999998 131.20000000000002-194.19999999999993 197.6-8.600000000000023 8.699999999999989-22.400000000000034 8.800000000000011-31 0-65-66-129.70000000000005-131.8-194.5-197.6-8.600000000000023-8.699999999999989-22.400000000000034-8.599999999999994-30.900000000000034 0.09999999999999432-15.699999999999989 16.200000000000017-31.099999999999994 32.30000000000001-47.099999999999994 48.80000000000001-8.5 8.800000000000011-8.299999999999983 23 0.20000000000001705 31.69999999999999 63.099999999999966 64.19999999999999 127.89999999999998 130.10000000000002 193.59999999999997 197 8.600000000000023 8.699999999999989 8.5 22.80000000000001 0 31.599999999999966-65.19999999999999 66.40000000000009-130.2 132.5-194.7 198.10000000000002-8.5 8.700000000000045-8.5 22.800000000000068 0.20000000000001705 31.399999999999977l47.79999999999998 47.90000000000009c8.600000000000023 8.599999999999909 22.600000000000023 8.599999999999909 31.100000000000023-0.10000000000002274l194.2-197.30000000000007c8.600000000000023-8.699999999999932 22.399999999999977-8.699999999999932 31 0 64.70000000000005 65.80000000000007 129.20000000000005 131.4000000000001 194.20000000000005 197.5 8.599999999999909 8.700000000000045 22.5 8.800000000000068 31 0.10000000000002274 16-16.199999999999932 31.699999999999932-32.19999999999993 47.59999999999991-48.299999999999955 8.600000000000023-8.700000000000045 8.600000000000023-22.899999999999977 0.10000000000002274-31.600000000000023-63.799999999999955-65-128.5-130.89999999999998-194.19999999999993-197.79999999999995-8.600000000000023-8.700000000000045-8.600000000000023-22.900000000000034 0-31.600000000000023 65.19999999999993-66.40000000000003 130.0999999999999-132.5 194.5-198.20000000000005 8.599999999999909-8.699999999999989 8.5-22.799999999999955-0.10000000000002274-31.49999999999997l-47.80000000000007-48.099999999999994c-8.5-8.5-22.399999999999977-8.400000000000006-31 0.29999999999998295z',
      })
    );
  const uS = (i) =>
    L.createElement(
      'svg',
      {
        xmlns: 'http://www.w3.org/2000/svg',
        viewBox: '0 0 448 512',
        width: i.width,
      },
      L.createElement('path', {
        fill: 'currentColor',
        d: 'M268 416h24a12 12 0 0 0 12-12V188a12 12 0 0 0-12-12h-24a12 12 0 0 0-12 12v216a12 12 0 0 0 12 12zM432 80h-82.41l-34-56.7A48 48 0 0 0 274.41 0H173.59a48 48 0 0 0-41.16 23.3L98.41 80H16A16 16 0 0 0 0 96v16a16 16 0 0 0 16 16h16v336a48 48 0 0 0 48 48h288a48 48 0 0 0 48-48V128h16a16 16 0 0 0 16-16V96a16 16 0 0 0-16-16zM171.84 50.91A6 6 0 0 1 177 48h94a6 6 0 0 1 5.15 2.91L293.61 80H154.39zM368 464H80V128h288zm-212-48h24a12 12 0 0 0 12-12V188a12 12 0 0 0-12-12h-24a12 12 0 0 0-12 12v216a12 12 0 0 0 12 12z',
      })
    );
  var cS = (i) => {
    const [t, e] = yt(false);
    const [n, r] = yt(false);
    const o = (h) => {
      e(true);
      r(false);
    };
    const s = (h) => {
      i.onDelete(i.body);
      r(false);
    };
    const a = (h) =>
      i.onUpdate(i.body, je(ue({}, i.body), { value: h.target.value }));
    const l = (h) =>
      i.onUpdate(i.body, je(ue({}, i.body), { purpose: h.value }));
    const u = i.body.modified || i.body.created;
    const c =
      i.body.creator &&
      L.createElement(
        'div',
        { className: 'r6o-lastmodified' },
        L.createElement(
          'span',
          { className: 'r6o-lastmodified-by' },
          i.body.creator.name || i.body.creator.id
        ),
        i.body.created &&
          L.createElement(
            'span',
            { className: 'r6o-lastmodified-at' },
            L.createElement(bf, {
              datetime: i.env.toClientTime(u),
              locale: Qe.locale(),
            })
          )
      );
    if (i.readOnly) {
      return L.createElement(
        'div',
        { className: 'r6o-widget comment' },
        L.createElement(
          'div',
          { className: 'r6o-readonly-comment' },
          i.body.value
        ),
        c
      );
    } else {
      return L.createElement(
        'div',
        { className: t ? 'r6o-widget comment editable' : 'r6o-widget comment' },
        L.createElement(Ou, {
          editable: t,
          content: i.body.value,
          onChange: a,
          onSaveAndClose: i.onSaveAndClose,
        }),
        !t && c,
        i.purposeSelector &&
          L.createElement(Zc, {
            editable: t,
            content: i.body.purpose,
            onChange: l,
            onSaveAndClose: i.onSaveAndClose,
          }),
        L.createElement(
          'div',
          {
            className: n
              ? 'r6o-icon r6o-arrow-down r6o-menu-open'
              : 'r6o-icon r6o-arrow-down',
            onClick: () => r(!n),
          },
          L.createElement(aS, { width: 12 })
        ),
        n &&
          L.createElement(Jy, {
            onEdit: o,
            onDelete: s,
            onClickOutside: () => r(false),
          })
      );
    }
  };
  const hS = Vo.map((i) => i.value);
  const Kc = (i, t) => {
    const e = t
      ? hS.indexOf(i.purpose) > -1
      : i.purpose == 'commenting' || i.purpose == 'replying';
    return i.type === 'TextualBody' && (!i.hasOwnProperty('purpose') || e);
  };
  const Jc = (i, t) => {
    var e;
    var n;
    if (t.editable === true) {
      return false;
    }
    if (t.editable === false) {
      return true;
    }
    if (t.editable === 'MINE_ONLY') {
      const r = (e = i.creator) == null ? void 0 : e.id;
      return ((n = t.env.user) == null ? void 0 : n.id) !== r;
    }
    return t.readOnly;
  };
  const dS = (i, t) =>
    i || {
      type: 'TextualBody',
      value: '',
      purpose: t ? 'replying' : 'commenting',
      draft: true,
    };
  const Qc = (i) => {
    const t = i.annotation
      ? i.annotation.bodies.filter((s) => Kc(s, i.purposeSelector))
      : [];
    const e = dS(
      t.find((s) => s.draft == true),
      t.length > 1
    );
    const n = t.filter((s) => s != e);
    const r = (s) => {
      const a = e.value;
      const l = s.target.value;
      if (a.length === 0 && l.length > 0) {
        i.onAppendBody(je(ue({}, e), { value: l }));
      } else if (a.length > 0 && l.length === 0) {
        i.onRemoveBody(e);
      } else {
        i.onUpdateBody(e, je(ue({}, e), { value: l }));
      }
    };
    const o = (s) => i.onUpdateBody(e, je(ue({}, e), { purpose: s.value }));
    return L.createElement(
      L.Fragment,
      null,
      n.map((s, a) =>
        L.createElement(cS, {
          key: a,
          env: i.env,
          purposeSelector: i.purposeSelector,
          readOnly: Jc(s, i),
          body: s,
          onUpdate: i.onUpdateBody,
          onDelete: i.onRemoveBody,
          onSaveAndClose: i.onSaveAndClose,
        })
      ),
      !i.readOnly &&
        i.annotation &&
        L.createElement(
          'div',
          { className: 'r6o-widget comment editable' },
          L.createElement(Ou, {
            focus: i.focus,
            content: e.value,
            editable: true,
            placeholder:
              n.length > 0 ? Qe.t('Add a reply...') : Qe.t('Add a comment...'),
            onChange: r,
            onSaveAndClose: () => i.onSaveAndClose(),
          }),
          i.purposeSelector &&
            e.value.length > 0 &&
            L.createElement(Zc, {
              editable: true,
              content: e.purpose,
              onChange: o,
              onSaveAndClose: () => i.onSaveAndClose(),
            })
        )
    );
  };
  Qc.disableDelete = (i, t) =>
    i.bodies.filter((n) => Kc(n, t.purposeSelector)).some((n) => Jc(n, t));
  var $c = Qc;
  var nh = { disabled: false };
  var ih = L.createContext(null);
  var Vn = 'unmounted';
  var Wt = 'exited';
  var jt = 'entering';
  var pn = 'entered';
  var Uo = 'exiting';
  var Et = (function () {
    function t(n, r) {
      var o = i.call(this, n, r) || this;
      var s = r;
      var a = s && !s.isMounting ? n.enter : n.appear;
      var l;
      o.appearStatus = null;
      if (n.in) {
        if (a) {
          l = Wt;
          o.appearStatus = jt;
        } else {
          l = pn;
        }
      } else if (n.unmountOnExit || n.mountOnEnter) {
        l = Vn;
      } else {
        l = Wt;
      }
      o.state = { status: l };
      o.nextCallback = null;
      return o;
    }
    var i = L.Component;
    eh(t, i);
    t.getDerivedStateFromProps = function (r, o) {
      var s = r.in;
      if (s && o.status === Vn) {
        return { status: Wt };
      } else {
        return null;
      }
    };
    var e = t.prototype;
    e.componentDidMount = function () {
      this.updateStatus(true, this.appearStatus);
    };
    e.componentDidUpdate = function (r) {
      var o = null;
      if (r !== this.props) {
        var s = this.state.status;
        if (this.props.in) {
          if (s !== jt && s !== pn) {
            o = jt;
          }
        } else if (s === jt || s === pn) {
          o = Uo;
        }
      }
      this.updateStatus(false, o);
    };
    e.componentWillUnmount = function () {
      this.cancelNextCallback();
    };
    e.getTimeouts = function () {
      var r = this.props.timeout;
      var s;
      var a;
      var o = (s = a = r);
      if (r != null && typeof r != 'number') {
        o = r.exit;
        s = r.enter;
        a = r.appear !== void 0 ? r.appear : s;
      }
      return { exit: o, enter: s, appear: a };
    };
    e.updateStatus = function (r, o) {
      if (r === void 0) {
        r = false;
      }
      if (o === null) {
        if (this.props.unmountOnExit && this.state.status === Wt) {
          this.setState({ status: Vn });
        }
      } else {
        this.cancelNextCallback();
        if (o === jt) {
          this.performEnter(r);
        } else {
          this.performExit();
        }
      }
    };
    e.performEnter = function (r) {
      var o = this;
      var s = this.props.enter;
      var a = this.context ? this.context.isMounting : r;
      var l = this.props.nodeRef ? [a] : [L.findDOMNode(this), a];
      var u = l[0];
      var c = l[1];
      var h = this.getTimeouts();
      var d = a ? h.appear : h.enter;
      if ((!r && !s) || nh.disabled) {
        this.safeSetState({ status: pn }, function () {
          o.props.onEntered(u);
        });
        return;
      }
      this.props.onEnter(u, c);
      this.safeSetState({ status: jt }, function () {
        o.props.onEntering(u, c);
        o.onTransitionEnd(d, function () {
          o.safeSetState({ status: pn }, function () {
            o.props.onEntered(u, c);
          });
        });
      });
    };
    e.performExit = function () {
      var r = this;
      var o = this.props.exit;
      var s = this.getTimeouts();
      var a = this.props.nodeRef ? void 0 : L.findDOMNode(this);
      if (!o || nh.disabled) {
        this.safeSetState({ status: Wt }, function () {
          r.props.onExited(a);
        });
        return;
      }
      this.props.onExit(a);
      this.safeSetState({ status: Uo }, function () {
        r.props.onExiting(a);
        r.onTransitionEnd(s.exit, function () {
          r.safeSetState({ status: Wt }, function () {
            r.props.onExited(a);
          });
        });
      });
    };
    e.cancelNextCallback = function () {
      if (this.nextCallback !== null) {
        this.nextCallback.cancel();
        this.nextCallback = null;
      }
    };
    e.safeSetState = function (r, o) {
      o = this.setNextCallback(o);
      this.setState(r, o);
    };
    e.setNextCallback = function (r) {
      var o = this;
      var s = true;
      this.nextCallback = function (a) {
        if (s) {
          s = false;
          o.nextCallback = null;
          r(a);
        }
      };
      this.nextCallback.cancel = function () {
        s = false;
      };
      return this.nextCallback;
    };
    e.onTransitionEnd = function (r, o) {
      this.setNextCallback(o);
      var s = this.props.nodeRef
        ? this.props.nodeRef.current
        : L.findDOMNode(this);
      var a = r == null && !this.props.addEndListener;
      if (!s || a) {
        setTimeout(this.nextCallback, 0);
        return;
      }
      if (this.props.addEndListener) {
        var l = this.props.nodeRef
          ? [this.nextCallback]
          : [s, this.nextCallback];
        var u = l[0];
        var c = l[1];
        this.props.addEndListener(u, c);
      }
      if (r != null) {
        setTimeout(this.nextCallback, r);
      }
    };
    e.render = function () {
      var r = this.state.status;
      if (r === Vn) {
        return null;
      }
      var o = this.props;
      var s = o.children;
      o.in;
      o.mountOnEnter;
      o.unmountOnExit;
      o.appear;
      o.enter;
      o.exit;
      o.timeout;
      o.addEndListener;
      o.onEnter;
      o.onEntering;
      o.onEntered;
      o.onExit;
      o.onExiting;
      o.onExited;
      o.nodeRef;
      var a = Po(o, [
        'children',
        'in',
        'mountOnEnter',
        'unmountOnExit',
        'appear',
        'enter',
        'exit',
        'timeout',
        'addEndListener',
        'onEnter',
        'onEntering',
        'onEntered',
        'onExit',
        'onExiting',
        'onExited',
        'nodeRef',
      ]);
      return L.createElement(
        ih.Provider,
        { value: null },
        typeof s == 'function' ? s(r, a) : L.cloneElement(L.Children.only(s), a)
      );
    };
    return t;
  })();
  Et.contextType = ih;
  Et.propTypes = {};
  Et.defaultProps = {
    in: false,
    mountOnEnter: false,
    unmountOnExit: false,
    appear: false,
    enter: true,
    exit: true,
    onEnter: mn,
    onEntering: mn,
    onEntered: mn,
    onExit: mn,
    onExiting: mn,
    onExited: mn,
  };
  Et.UNMOUNTED = Vn;
  Et.EXITED = Wt;
  Et.ENTERING = jt;
  Et.ENTERED = pn;
  Et.EXITING = Uo;
  var gS = Et;
  var vS = function (t, e) {
    return (
      t &&
      e &&
      e.split(' ').forEach(function (n) {
        return pS(t, n);
      })
    );
  };
  var Wo = function (t, e) {
    return (
      t &&
      e &&
      e.split(' ').forEach(function (n) {
        return mS(t, n);
      })
    );
  };
  var jo = (function () {
    function t() {
      var r = arguments.length;
      var o = new Array(r);
      for (var s = 0; s < r; s++) {
        o[s] = arguments[s];
      }
      var n = i.call.apply(i, [this].concat(o)) || this;
      n.appliedClasses = { appear: {}, enter: {}, exit: {} };
      n.onEnter = function (a, l) {
        var u = n.resolveArguments(a, l);
        var c = u[0];
        var h = u[1];
        n.removeClasses(c, 'exit');
        n.addClass(c, h ? 'appear' : 'enter', 'base');
        if (n.props.onEnter) {
          n.props.onEnter(a, l);
        }
      };
      n.onEntering = function (a, l) {
        var u = n.resolveArguments(a, l);
        var c = u[0];
        var h = u[1];
        var d = h ? 'appear' : 'enter';
        n.addClass(c, d, 'active');
        if (n.props.onEntering) {
          n.props.onEntering(a, l);
        }
      };
      n.onEntered = function (a, l) {
        var u = n.resolveArguments(a, l);
        var c = u[0];
        var h = u[1];
        var d = h ? 'appear' : 'enter';
        n.removeClasses(c, d);
        n.addClass(c, d, 'done');
        if (n.props.onEntered) {
          n.props.onEntered(a, l);
        }
      };
      n.onExit = function (a) {
        var l = n.resolveArguments(a);
        var u = l[0];
        n.removeClasses(u, 'appear');
        n.removeClasses(u, 'enter');
        n.addClass(u, 'exit', 'base');
        if (n.props.onExit) {
          n.props.onExit(a);
        }
      };
      n.onExiting = function (a) {
        var l = n.resolveArguments(a);
        var u = l[0];
        n.addClass(u, 'exit', 'active');
        if (n.props.onExiting) {
          n.props.onExiting(a);
        }
      };
      n.onExited = function (a) {
        var l = n.resolveArguments(a);
        var u = l[0];
        n.removeClasses(u, 'exit');
        n.addClass(u, 'exit', 'done');
        if (n.props.onExited) {
          n.props.onExited(a);
        }
      };
      n.resolveArguments = function (a, l) {
        if (n.props.nodeRef) {
          return [n.props.nodeRef.current, a];
        } else {
          return [a, l];
        }
      };
      n.getClassNames = function (a) {
        var l = n.props.classNames;
        var u = typeof l == 'string';
        var c = u && l ? l + '-' : '';
        var h = u ? '' + c + a : l[a];
        var d = u ? h + '-active' : l[a + 'Active'];
        var g = u ? h + '-done' : l[a + 'Done'];
        return { baseClassName: h, activeClassName: d, doneClassName: g };
      };
      return n;
    }
    var i = L.Component;
    eh(t, i);
    var e = t.prototype;
    e.addClass = function (r, o, s) {
      var a = this.getClassNames(o)[s + 'ClassName'];
      var l = this.getClassNames('enter');
      var u = l.doneClassName;
      if (o === 'appear' && s === 'done' && u) {
        a += ' ' + u;
      }
      if (s === 'active' && r) {
        r.scrollTop;
      }
      if (a) {
        this.appliedClasses[o][s] = a;
        vS(r, a);
      }
    };
    e.removeClasses = function (r, o) {
      var s = this.appliedClasses[o];
      var a = s.base;
      var l = s.active;
      var u = s.done;
      this.appliedClasses[o] = {};
      if (a) {
        Wo(r, a);
      }
      if (l) {
        Wo(r, l);
      }
      if (u) {
        Wo(r, u);
      }
    };
    e.render = function () {
      var r = this.props;
      r.classNames;
      var o = Po(r, ['classNames']);
      return L.createElement(
        gS,
        ne({}, o, {
          onEnter: this.onEnter,
          onEntered: this.onEntered,
          onEntering: this.onEntering,
          onExit: this.onExit,
          onExiting: this.onExiting,
          onExited: this.onExited,
        })
      );
    };
    return t;
  })();
  jo.defaultProps = { classNames: '' };
  jo.propTypes = {};
  var yS = jo;
  const wS = (i, t) =>
    t.filter((e) =>
      (e.label ? e.label : e).toLowerCase().startsWith(i.toLowerCase())
    );
  const bS = (i, t) => t(i);
  var SS = (i) => {
    const t = ut();
    const [e, n] = yt(i.initialValue || '');
    const [r, o] = yt([]);
    const [s, a] = yt(null);
    kt(() => {
      if (i.focus) {
        t.current.querySelector('input').focus({ preventScroll: true });
      }
    }, []);
    kt(() => {
      if (i.onChange) {
        i.onChange(e);
      }
    }, [e]);
    const l = (d) => {
      if (typeof i.vocabulary == 'function') {
        const g = bS(d, i.vocabulary);
        if (g.then) {
          g.then(o);
        } else {
          o(g);
        }
      } else {
        const g = wS(d, i.vocabulary);
        o(g);
      }
    };
    const u = () => {
      if (s === null) {
        const d = e.trim();
        if (d) {
          const g = Array.isArray(i.vocabulary)
            ? i.vocabulary.find(
                (y) => (y.label || y).toLowerCase() === d.toLowerCase()
              )
            : null;
          if (g) {
            i.onSubmit(g);
          } else {
            i.onSubmit(d);
          }
        }
      } else {
        i.onSubmit(r[s]);
      }
      n('');
      o([]);
      a(null);
    };
    const c = (d) => {
      if (d.which === 13) {
        u();
      } else if (d.which === 27) {
        if (i.onCancel) {
          i.onCancel();
        }
      } else if (r.length > 0) {
        if (d.which === 38) {
          if (s === null) {
            a(0);
          } else {
            const g = Math.max(0, s - 1);
            a(g);
          }
        } else if (d.which === 40) {
          if (s === null) {
            a(0);
          } else {
            const g = Math.min(r.length - 1, s + 1);
            a(g);
          }
        }
      } else if (d.which === 40 && Array.isArray(i.vocabulary)) {
        o(i.vocabulary);
      }
    };
    const h = (d) => {
      const { value: g } = d.target;
      n(g);
      a(null);
      if (g) {
        l(g);
      } else {
        o([]);
      }
    };
    return L.createElement(
      'div',
      { ref: t, className: 'r6o-autocomplete' },
      L.createElement(
        'div',
        null,
        L.createElement('input', {
          onKeyDown: c,
          onChange: h,
          value: e,
          placeholder: i.placeholder,
        })
      ),
      L.createElement(
        'ul',
        null,
        r.length > 0 &&
          r.map((d, g) =>
            L.createElement(
              'li',
              {
                key: `${d.label ? d.label : d}${g}`,
                onClick: u,
                onMouseEnter: () => a(g),
                style: s === g ? { backgroundColor: '#bde4ff' } : {},
              },
              d.label ? d.label : d
            )
          )
      )
    );
  };
  const ES = (i) =>
    i || { type: 'TextualBody', value: '', purpose: 'tagging', draft: true };
  var rh = (i) => {
    const t = i.annotation
      ? i.annotation.bodies.filter((h) => h.purpose === 'tagging')
      : [];
    const e = ES(
      t
        .slice()
        .reverse()
        .find((h) => h.draft)
    );
    const n = t.filter((h) => h != e);
    const [r, o] = yt(false);
    const s = (h) => (d) => {
      o(r === h ? false : h);
    };
    const a = (h) => {
      const d = e.value.trim();
      const g = h.trim();
      if (d.length === 0 && g.length > 0) {
        i.onAppendBody(je(ue({}, e), { value: g }));
      } else if (d.length > 0 && g.length === 0) {
        i.onRemoveBody(e);
      } else {
        i.onUpdateBody(e, je(ue({}, e), { value: g }));
      }
    };
    const l = (h) => (d) => {
      d.stopPropagation();
      i.onRemoveBody(h);
    };
    const u = (h) => {
      const d = h.uri
        ? {
            type: 'SpecificResource',
            purpose: 'tagging',
            source: { id: h.uri, label: h.label },
          }
        : { type: 'TextualBody', purpose: 'tagging', value: h.label || h };
      if (e.value.trim().length === 0) {
        i.onAppendBody(d);
      } else {
        i.onUpdateBody(e, d);
      }
    };
    const c = (h) => h.value || h.source.label;
    return L.createElement(
      'div',
      { className: 'r6o-widget r6o-tag' },
      n.length > 0 &&
        L.createElement(
          'ul',
          { className: 'r6o-taglist' },
          n.map((h) =>
            L.createElement(
              'li',
              { key: c(h), onClick: s(h) },
              L.createElement('span', { className: 'r6o-label' }, c(h)),
              !i.readOnly &&
                L.createElement(
                  yS,
                  { in: r === h, timeout: 200, classNames: 'r6o-delete' },
                  L.createElement(
                    'span',
                    { className: 'r6o-delete-wrapper', onClick: l(h) },
                    L.createElement(
                      'span',
                      { className: 'r6o-delete' },
                      L.createElement(lS, { width: 12 })
                    )
                  )
                )
            )
          )
        ),
      !i.readOnly &&
        L.createElement(SS, {
          focus: i.focus,
          placeholder: Qe.t('Add tag...'),
          vocabulary: i.vocabulary || [],
          onChange: a,
          onSubmit: u,
        })
    );
  };
  class oh extends Oe {
    constructor(t) {
      super(t);
      this.element = L.createRef();
    }
    renderWidget(t) {
      const e = this.props.widget(
        je(ue({ annotation: t.annotation, readOnly: t.readOnly }, t.config), {
          onAppendBody: (n, r) => t.onAppendBody(n, r),
          onUpdateBody: (n, r, o) => t.onUpdateBody(n, r, o),
          onUpsertBody: (n, r, o) => t.onUpsertBody(n, r, o),
          onRemoveBody: (n, r) => t.onRemoveBody(n, r),
          onBatchModify: (n, r) => t.onBatchModify(n, r),
          onSetProperty: (n, r) => t.onSetProperty(n, r),
          onSaveAndClose: () => t.onSaveAndClose(),
        })
      );
      while (this.element.current.firstChild) {
        this.element.current.removeChild(this.element.current.lastChild);
      }
      this.element.current.appendChild(e);
    }
    componentDidMount() {
      this.renderWidget(this.props);
    }
    componentWillReceiveProps(t) {
      if (this.element.current && this.props.annotation !== t.annotation) {
        this.renderWidget(t);
      }
    }
    render() {
      return L.createElement('div', { ref: this.element, className: 'widget' });
    }
  }
  window.React = L;
  window.ReactDOM = L;
  const _S = { COMMENT: $c, TAG: rh };
  const xS = [L.createElement($c, null), L.createElement(rh, null)];
  const TS = (i) => {
    const t = (n) => {
      var r;
      return (
        typeof n == 'function' &&
        !!((r = n.prototype) == null ? void 0 : r.isReactComponent)
      );
    };
    const e = (n) =>
      typeof n == 'function' &&
      (String(n).match(/return .+\(['|"].+['|"],\s*\{/g) ||
        String(n).match(/return .+preact_compat/) ||
        String(n).match(/return .+\.createElement/g));
    return t(i) || e(i);
  };
  const CS = (i) => {
    const t = (n, r, o) => {
      if (typeof n == 'string' || n instanceof String) {
        return L.createElement(_S[n], r);
      }
      if ((o == null ? void 0 : o.toLowerCase()) === 'react') {
        return L.createElement(n, r);
      }
      if ((o == null ? void 0 : o.toLowerCase()) === 'plainjs') {
        return L.createElement(oh, { widget: n, config: r });
      }
      if (TS(n)) {
        return L.createElement(n, r);
      }
      if (typeof n == 'function' || n instanceof Function) {
        return L.createElement(oh, { widget: n, config: r });
      }
      throw `${n} is not a valid plugin`;
    };
    if (i.widget) {
      const e = i;
      const { widget: n, force: r } = e;
      const o = hs(e, ['widget', 'force']);
      return t(n, o, r);
    } else {
      return t(i);
    }
  };
  const sh = 14;
  var ah = (i, t, e, n) => {
    const r = i.getBoundingClientRect();
    t.className = 'r6o-editor r6o-arrow-top r6o-arrow-left';
    const { left: o, top: s, right: a, bottom: l } = e.getBoundingClientRect();
    t.style.top = `${l - r.top + sh}px`;
    t.style.left = `${o - r.left}px`;
    if (n) {
      const u = t.children[1].getBoundingClientRect();
      if (u.right > window.innerWidth) {
        t.classList.remove('r6o-arrow-left');
        t.classList.add('r6o-arrow-right');
        t.style.left = `${a - u.width - r.left}px`;
      }
      if (u.bottom > window.innerHeight) {
        t.classList.remove('r6o-arrow-top');
        t.classList.add('r6o-arrow-bottom');
        const h = t.children[1].getBoundingClientRect().height;
        t.style.top = `${s - r.top - h - sh}px`;
      }
      const c = t.children[1].getBoundingClientRect();
      if (c.top < 0) {
        t.classList.add('pushed', 'down');
        t.style.top = `${-r.top}px`;
        const h = l - r.top;
        if (c.height - r.top > h) {
          t.classList.remove('r6o-arrow-bottom');
        }
      }
      if (c.left < 0) {
        t.classList.add('pushed', 'right');
        t.style.left = `${-r.left}px`;
      }
      requestAnimationFrame(() => (t.style.opacity = 1));
    }
  };
  const lh = (i) => {
    const { top: t, left: e, width: n, height: r } = i.getBoundingClientRect();
    return `${t}, ${e}, ${n}, ${r}`;
  };
  class PS extends Oe {
    constructor(t) {
      super(t);
      P(this, 'initResizeObserver', () => {
        const t =
          this.props.autoPosition === void 0 ? true : this.props.autoPosition;
        if (window == null ? void 0 : window.ResizeObserver) {
          const e = new ResizeObserver(() => {
            if (!this.state.dragged) {
              ah(
                this.props.wrapperEl,
                this.element.current,
                this.props.selectedElement,
                t
              );
            }
          });
          e.observe(this.props.wrapperEl);
          return () => e.disconnect();
        } else if (!this.state.dragged) {
          ah(
            this.props.wrapperEl,
            this.element.current,
            this.props.selectedElement,
            t
          );
        }
      });
      P(this, 'creationMeta', (t) => {
        const e = {};
        const { user: n } = this.props.env;
        if (n) {
          e.creator = {};
          if (n.id) {
            e.creator.id = n.id;
          }
          if (n.displayName) {
            e.creator.name = n.displayName;
          }
          e[t.created ? 'modified' : 'created'] =
            this.props.env.getCurrentTimeAdjusted();
        }
        return e;
      });
      P(this, 'getCurrentAnnotation', () =>
        this.state.currentAnnotation.clone()
      );
      P(this, 'updateCurrentAnnotation', (t, e) =>
        this.setState(
          { currentAnnotation: this.state.currentAnnotation.clone(t) },
          () => {
            if (e) {
              this.onOk();
            }
          }
        )
      );
      P(this, 'onAppendBody', (t, e) =>
        this.updateCurrentAnnotation(
          {
            body: [
              ...this.state.currentAnnotation.bodies,
              ue(ue({}, t), this.creationMeta(t)),
            ],
          },
          e
        )
      );
      P(this, 'onUpdateBody', (t, e, n) =>
        this.updateCurrentAnnotation(
          {
            body: this.state.currentAnnotation.bodies.map((r) =>
              r === t ? ue(ue({}, e), this.creationMeta(e)) : r
            ),
          },
          n
        )
      );
      P(this, 'onRemoveBody', (t, e) =>
        this.updateCurrentAnnotation(
          { body: this.state.currentAnnotation.bodies.filter((n) => n !== t) },
          e
        )
      );
      P(this, 'onUpsertBody', (t, e, n) => {
        if (t == null && e != null) {
          this.onAppendBody(e, n);
        } else if (t != null && e != null) {
          this.onUpdateBody(t, e, n);
        } else if (t != null && e == null) {
          const r = this.state.currentAnnotation.bodies.find(
            (o) => o.purpose === t.purpose
          );
          if (r) {
            this.onUpdateBody(r, t, n);
          } else {
            this.onAppendBody(t, n);
          }
        }
      });
      P(this, 'onBatchModify', (t, e) => {
        const n = t
          .filter((l) => l.action === 'upsert' && l.body)
          .map((l) => ({
            previous: this.state.currentAnnotation.bodies.find(
              (u) => u.purpose === l.body.purpose
            ),
            updated: ue(ue({}, l.body), this.creationMeta(l.body)),
          }));
        const r = t.filter((l) => l.action === 'remove').map((l) => l.body);
        const o = [
          ...t
            .filter(
              (l) =>
                l.action === 'append' ||
                (l.action === 'upsert' && l.updated && !l.previous)
            )
            .map((l) => ue(ue({}, l.body), this.creationMeta(l.body))),
          ...n.filter((l) => !l.previous).map((l) => l.updated),
        ];
        const s = [
          ...t
            .filter(
              (l) =>
                l.action === 'update' ||
                (l.action === 'upsert' && l.updated && l.previous)
            )
            .map((l) => ({
              previous: l.previous,
              updated: ue(ue({}, l.updated), this.creationMeta(l.updated)),
            })),
          ...n.filter((l) => l.previous),
        ];
        const a = [
          ...this.state.currentAnnotation.bodies
            .filter((l) => !r.includes(l))
            .map((l) => {
              const u = s.find((c) => c.previous === l);
              if (u) {
                return u.updated;
              } else {
                return l;
              }
            }),
          ...o,
        ];
        this.updateCurrentAnnotation({ body: a }, e);
      });
      P(this, 'onSetProperty', (t, e) => {
        if (['@context', 'id', 'type', 'body', 'target'].includes(t)) {
          throw new Exception(`Cannot set ${t} - not allowed`);
        }
        if (e) {
          this.updateCurrentAnnotation({ [t]: e });
        } else {
          const r = this.currentAnnotation.clone();
          delete r[t];
          this.setState({ currentAnnotation: r });
        }
      });
      P(this, 'onCancel', () => this.props.onCancel(this.props.annotation));
      P(this, 'onOk', () => {
        const t = (n) =>
          n.clone({
            body: n.bodies.map((s) => {
              var a = s;
              var { draft: r } = a;
              var o = hs(a, ['draft']);
              return o;
            }),
          });
        const { currentAnnotation: e } = this.state;
        if (e.bodies.length === 0 && !this.props.allowEmpty) {
          if (e.isSelection) {
            this.onCancel();
          } else {
            this.props.onAnnotationDeleted(this.props.annotation);
          }
        } else if (e.isSelection) {
          this.props.onAnnotationCreated(t(e).toAnnotation());
        } else {
          this.props.onAnnotationUpdated(t(e), this.props.annotation);
        }
      });
      P(this, 'onDelete', () =>
        this.props.onAnnotationDeleted(this.props.annotation)
      );
      this.element = L.createRef();
      this.state = {
        currentAnnotation: t.annotation,
        dragged: false,
        selectionBounds: lh(t.selectedElement),
      };
    }
    componentWillReceiveProps(t) {
      var r;
      const { selectionBounds: e } = this.state;
      const n = lh(t.selectedElement);
      if (
        (r = this.props.annotation) == null ? void 0 : r.isEqual(t.annotation)
      ) {
        this.setState({ selectionBounds: n });
      } else {
        this.setState({ currentAnnotation: t.annotation, selectionBounds: n });
      }
      if (
        this.props.modifiedTarget != t.modifiedTarget &&
        this.state.currentAnnotation
      ) {
        this.updateCurrentAnnotation({ target: this.props.modifiedTarget });
      }
      if (e != n && this.element.current) {
        if (this.removeObserver) {
          this.removeObserver();
        }
        this.removeObserver = this.initResizeObserver();
      }
    }
    componentDidMount() {
      this.removeObserver = this.initResizeObserver();
      new MutationObserver(() => {
        if (this.element.current) {
          if (this.removeObserver) {
            this.removeObserver();
          }
          this.removeObserver = this.initResizeObserver();
        }
      }).observe(this.element.current, { childList: true, subtree: true });
    }
    componentWillUnmount() {
      if (this.removeObserver) {
        this.removeObserver();
      }
    }
    render() {
      const { currentAnnotation: t } = this.state;
      const e = this.props.widgets ? this.props.widgets.map(CS) : xS;
      const n = (o) =>
        o.type.disableDelete
          ? o.type.disableDelete(
              t,
              je(ue({}, o.props), {
                readOnly: this.props.readOnly,
                env: this.props.env,
              })
            )
          : false;
      const r =
        t &&
        (t.bodies.length > 0 || this.props.allowEmpty) &&
        !this.props.readOnly &&
        !t.isSelection &&
        !e.some(n);
      return L.createElement(
        af,
        {
          disabled: !this.props.detachable,
          handle: '.r6o-draggable',
          cancel: '.r6o-btn, .r6o-btn *',
          onDrag: () => this.setState({ dragged: true }),
        },
        L.createElement(
          'div',
          {
            ref: this.element,
            className: this.state.dragged ? 'r6o-editor dragged' : 'r6o-editor',
          },
          L.createElement('div', { className: 'r6o-arrow' }),
          L.createElement(
            'div',
            { className: 'r6o-editor-inner' },
            e.map((o, s) =>
              L.cloneElement(o, {
                key: `${s}`,
                focus: s === 0,
                annotation: t,
                readOnly: this.props.readOnly,
                env: this.props.env,
                onAppendBody: this.onAppendBody,
                onUpdateBody: this.onUpdateBody,
                onRemoveBody: this.onRemoveBody,
                onUpsertBody: this.onUpsertBody,
                onBatchModify: this.onBatchModify,
                onSetProperty: this.onSetProperty,
                onSaveAndClose: this.onOk,
              })
            ),
            this.props.readOnly
              ? L.createElement(
                  'div',
                  { className: 'r6o-footer' },
                  L.createElement(
                    'button',
                    { className: 'r6o-btn', onClick: this.onCancel },
                    Qe.t('Close')
                  )
                )
              : L.createElement(
                  'div',
                  {
                    className: this.props.detachable
                      ? 'r6o-footer r6o-draggable'
                      : 'r6o-footer',
                  },
                  r &&
                    L.createElement(
                      'button',
                      {
                        className: 'r6o-btn left delete-annotation',
                        title: Qe.t('Delete'),
                        onClick: this.onDelete,
                      },
                      L.createElement(uS, { width: 12 })
                    ),
                  L.createElement(
                    'button',
                    { className: 'r6o-btn outline', onClick: this.onCancel },
                    Qe.t('Cancel')
                  ),
                  L.createElement(
                    'button',
                    { className: 'r6o-btn ', onClick: this.onOk },
                    Qe.t('Ok')
                  )
                )
          )
        )
      );
    }
  }
  var Qi;
  var AS = new Uint8Array(16);
  var DS =
    /^(?:[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}|00000000-0000-0000-0000-000000000000)$/i;
  var Ie = [];
  for (var Go = 0; Go < 256; ++Go) {
    Ie.push((Go + 256).toString(16).substr(1));
  }
  var FS = function i(t, e) {
    if (t === e) {
      return true;
    }
    if (t && e && typeof t == 'object' && typeof e == 'object') {
      if (t.constructor !== e.constructor) {
        return false;
      }
      var n;
      var r;
      var o;
      if (Array.isArray(t)) {
        n = t.length;
        if (n != e.length) {
          return false;
        }
        for (r = n; r-- != 0; ) {
          if (!i(t[r], e[r])) {
            return false;
          }
        }
        return true;
      }
      if (t.constructor === RegExp) {
        return t.source === e.source && t.flags === e.flags;
      }
      if (t.valueOf !== Object.prototype.valueOf) {
        return t.valueOf() === e.valueOf();
      }
      if (t.toString !== Object.prototype.toString) {
        return t.toString() === e.toString();
      }
      o = Object.keys(t);
      n = o.length;
      if (n !== Object.keys(e).length) {
        return false;
      }
      for (r = n; r-- != 0; ) {
        if (!Object.prototype.hasOwnProperty.call(e, o[r])) {
          return false;
        }
      }
      for (r = n; r-- != 0; ) {
        var s = o[r];
        if (!i(t[s], e[s])) {
          return false;
        }
      }
      return true;
    }
    return t !== t && e !== e;
  };
  var ch = FS;
  const ar = class {
    constructor(t, e) {
      P(
        this,
        'clone',
        (t, e) =>
          new ar(ue(ue({}, this.underlying), t), ue(ue({}, this.opts), e))
      );
      P(this, 'selector', (t) => {
        const { target: e } = this.underlying;
        if (e.selector) {
          return (Array.isArray(e.selector) ? e.selector : [e.selector]).find(
            (r) => r.type === t
          );
        }
      });
      this.underlying = t;
      this.opts = e;
    }
    isEqual(t) {
      if ((t == null ? void 0 : t.type) === 'Annotation') {
        if (this.underlying === t.underlying) {
          return true;
        } else if (!this.underlying.id || !t.underlying.id) {
          return false;
        } else {
          return ch(this.underlying, t.underlying);
        }
      } else {
        return false;
      }
    }
    get readOnly() {
      var t;
      if ((t = this.opts) == null) {
        return;
      } else {
        return t.readOnly;
      }
    }
    get id() {
      return this.underlying.id;
    }
    get type() {
      return this.underlying.type;
    }
    get motivation() {
      return this.underlying.motivation;
    }
    get body() {
      return this.underlying.body;
    }
    get target() {
      return this.underlying.target;
    }
    get bodies() {
      if (Array.isArray(this.underlying.body)) {
        return this.underlying.body;
      } else {
        return [this.underlying.body];
      }
    }
    set bodies(t) {
      this.underlying.body = t;
    }
    get targets() {
      if (Array.isArray(this.underlying.target)) {
        return this.underlying.target;
      } else {
        return [this.underlying.target];
      }
    }
    get quote() {
      var t;
      if ((t = this.selector('TextQuoteSelector')) == null) {
        return;
      } else {
        return t.exact;
      }
    }
    get start() {
      var t;
      if ((t = this.selector('TextPositionSelector')) == null) {
        return;
      } else {
        return t.start;
      }
    }
    get end() {
      var t;
      if ((t = this.selector('TextPositionSelector')) == null) {
        return;
      } else {
        return t.end;
      }
    }
  };
  let Dt = ar;
  P(Dt, 'create', (t) => {
    const e = {
      '@context': 'http://www.w3.org/ns/anno.jsonld',
      type: 'Annotation',
      id: `#${uh()}`,
      body: [],
    };
    return new ar(ue(ue({}, e), t));
  });
  class Un {
    constructor(t, e) {
      P(this, 'clone', (t) => {
        const e = new Un();
        e.underlying = JSON.parse(JSON.stringify(this.underlying));
        if (t) {
          e.underlying = ue(ue({}, e.underlying), t);
        }
        return e;
      });
      P(this, 'selector', (t) => {
        const { target: e } = this.underlying;
        if (e.selector) {
          return (Array.isArray(e.selector) ? e.selector : [e.selector]).find(
            (r) => r.type === t
          );
        }
      });
      P(this, 'toAnnotation', () => {
        const t = Object.assign({}, this.underlying, {
          '@context': 'http://www.w3.org/ns/anno.jsonld',
          type: 'Annotation',
          id: `#${uh()}`,
        });
        return new Dt(t);
      });
      this.underlying = { type: 'Selection', body: e || [], target: t };
    }
    get type() {
      return this.underlying.type;
    }
    get body() {
      return this.underlying.body;
    }
    get target() {
      return this.underlying.target;
    }
    get targets() {
      if (Array.isArray(this.underlying.target)) {
        return this.underlying.target;
      } else {
        return [this.underlying.target];
      }
    }
    isEqual(t) {
      if (t) {
        return ch(this.underlying, t.underlying);
      } else {
        return false;
      }
    }
    get bodies() {
      if (Array.isArray(this.underlying.body)) {
        return this.underlying.body;
      } else {
        return [this.underlying.body];
      }
    }
    get quote() {
      var t;
      if ((t = this.selector('TextQuoteSelector')) == null) {
        return;
      } else {
        return t.exact;
      }
    }
    get isSelection() {
      return true;
    }
  }
  let qo = 0;
  var LS = () => ({
    setServerTime: (i) => {
      const t = Date.now();
      qo = i - t;
    },
    getCurrentTimeAdjusted: () => new Date(Date.now() + qo).toISOString(),
    toClientTime: (i) => Date.parse(i) - qo,
  });
  const kS = (i, t) => {
    if (i) {
      const e =
        i === 'auto'
          ? window.navigator.userLanguage || window.navigator.language
          : i;
      try {
        Qe.init(e.split('-')[0].toLowerCase(), t);
      } catch {
        console.warn(`Unsupported locale '${e}'. Falling back to default en.`);
      }
    } else {
      Qe.init(null, t);
    }
  };
  var hh = { exports: {} };
  (function () {
    function t(e) {
      return new t.Viewer(e);
    }
    var i = hh;
    (function () {
      var e = t;
      e.version = {
        versionStr: '3.0.0',
        major: parseInt('3', 10),
        minor: parseInt('0', 10),
        revision: parseInt('0', 10),
      };
      var n = {
        '[object Boolean]': 'boolean',
        '[object Number]': 'number',
        '[object String]': 'string',
        '[object Function]': 'function',
        '[object Array]': 'array',
        '[object Date]': 'date',
        '[object RegExp]': 'regexp',
        '[object Object]': 'object',
      };
      var r = Object.prototype.toString;
      var o = Object.prototype.hasOwnProperty;
      e.isFunction = function (s) {
        return e.type(s) === 'function';
      };
      e.isArray =
        Array.isArray ||
        function (s) {
          return e.type(s) === 'array';
        };
      e.isWindow = function (s) {
        return s && typeof s == 'object' && 'setInterval' in s;
      };
      e.type = function (s) {
        if (s == null) {
          return String(s);
        } else {
          return n[r.call(s)] || 'object';
        }
      };
      e.isPlainObject = function (s) {
        if (
          !s ||
          t.type(s) !== 'object' ||
          s.nodeType ||
          e.isWindow(s) ||
          (s.constructor &&
            !o.call(s, 'constructor') &&
            !o.call(s.constructor.prototype, 'isPrototypeOf'))
        ) {
          return false;
        }
        var a;
        for (var l in s) {
          a = l;
        }
        return a === void 0 || o.call(s, a);
      };
      e.isEmptyObject = function (s) {
        for (var a in s) {
          return false;
        }
        return true;
      };
      e.freezeObject = function (s) {
        if (Object.freeze) {
          e.freezeObject = Object.freeze;
        } else {
          e.freezeObject = function (a) {
            return a;
          };
        }
        return e.freezeObject(s);
      };
      e.supportsCanvas = (function () {
        var s = document.createElement('canvas');
        return !!e.isFunction(s.getContext) && !!s.getContext('2d');
      })();
      e.isCanvasTainted = function (s) {
        var a = false;
        try {
          s.getContext('2d').getImageData(0, 0, 1, 1);
        } catch {
          a = true;
        }
        return a;
      };
      e.supportsAddEventListener =
        !!document.documentElement.addEventListener &&
        !!document.addEventListener;
      e.supportsRemoveEventListener =
        !!document.documentElement.removeEventListener &&
        !!document.removeEventListener;
      e.supportsEventListenerOptions = (function () {
        var s = 0;
        if (e.supportsAddEventListener) {
          try {
            var a = {
              get capture() {
                s++;
                return false;
              },
              get once() {
                s++;
                return false;
              },
              get passive() {
                s++;
                return false;
              },
            };
            window.addEventListener('test', null, a);
            window.removeEventListener('test', null, a);
          } catch {
            s = 0;
          }
        }
        return s >= 3;
      })();
      e.getCurrentPixelDensityRatio = function () {
        if (e.supportsCanvas) {
          var s = document.createElement('canvas').getContext('2d');
          var a = window.devicePixelRatio || 1;
          var l =
            s.webkitBackingStorePixelRatio ||
            s.mozBackingStorePixelRatio ||
            s.msBackingStorePixelRatio ||
            s.oBackingStorePixelRatio ||
            s.backingStorePixelRatio ||
            1;
          return Math.max(a, 1) / l;
        } else {
          return 1;
        }
      };
      e.pixelDensityRatio = e.getCurrentPixelDensityRatio();
    })();
    (function () {
      function a(l, u) {
        if (u && l !== document.body) {
          return document.body;
        } else {
          return l.offsetParent;
        }
      }
      var e = t;
      e.extend = function () {
        var l;
        var u;
        var c;
        var h;
        var d;
        var g;
        var y = arguments[0] || {};
        var x = arguments.length;
        var b = false;
        var T = 1;
        if (typeof y == 'boolean') {
          b = y;
          y = arguments[1] || {};
          T = 2;
        }
        if (typeof y != 'object' && !t.isFunction(y)) {
          y = {};
        }
        for (x === T && ((y = this), --T); T < x; T++) {
          l = arguments[T];
          if (l !== null || l !== void 0) {
            for (u in l) {
              c = y[u];
              h = l[u];
              if (y !== h) {
                if (b && h && (t.isPlainObject(h) || (d = t.isArray(h)))) {
                  if (d) {
                    d = false;
                    g = c && t.isArray(c) ? c : [];
                  } else {
                    g = c && t.isPlainObject(c) ? c : {};
                  }
                  y[u] = t.extend(b, g, h);
                } else if (h !== void 0) {
                  y[u] = h;
                }
              }
            }
          }
        }
        return y;
      };
      var n = function () {
        if (typeof navigator != 'object') {
          return false;
        }
        var l = navigator.userAgent;
        if (typeof l == 'string') {
          return (
            l.indexOf('iPhone') !== -1 ||
            l.indexOf('iPad') !== -1 ||
            l.indexOf('iPod') !== -1
          );
        } else {
          return false;
        }
      };
      e.extend(e, {
        DEFAULT_SETTINGS: {
          xmlPath: null,
          tileSources: null,
          tileHost: null,
          initialPage: 0,
          crossOriginPolicy: false,
          ajaxWithCredentials: false,
          loadTilesWithAjax: false,
          ajaxHeaders: {},
          panHorizontal: true,
          panVertical: true,
          constrainDuringPan: false,
          wrapHorizontal: false,
          wrapVertical: false,
          visibilityRatio: 0.5,
          minPixelRatio: 0.5,
          defaultZoomLevel: 0,
          minZoomLevel: null,
          maxZoomLevel: null,
          homeFillsViewer: false,
          clickTimeThreshold: 300,
          clickDistThreshold: 5,
          dblClickTimeThreshold: 300,
          dblClickDistThreshold: 20,
          springStiffness: 6.5,
          animationTime: 1.2,
          gestureSettingsMouse: {
            dragToPan: true,
            scrollToZoom: true,
            clickToZoom: true,
            dblClickToZoom: false,
            pinchToZoom: false,
            zoomToRefPoint: true,
            flickEnabled: false,
            flickMinSpeed: 120,
            flickMomentum: 0.25,
            pinchRotate: false,
          },
          gestureSettingsTouch: {
            dragToPan: true,
            scrollToZoom: false,
            clickToZoom: false,
            dblClickToZoom: true,
            pinchToZoom: true,
            zoomToRefPoint: true,
            flickEnabled: true,
            flickMinSpeed: 120,
            flickMomentum: 0.25,
            pinchRotate: false,
          },
          gestureSettingsPen: {
            dragToPan: true,
            scrollToZoom: false,
            clickToZoom: true,
            dblClickToZoom: false,
            pinchToZoom: false,
            zoomToRefPoint: true,
            flickEnabled: false,
            flickMinSpeed: 120,
            flickMomentum: 0.25,
            pinchRotate: false,
          },
          gestureSettingsUnknown: {
            dragToPan: true,
            scrollToZoom: false,
            clickToZoom: false,
            dblClickToZoom: true,
            pinchToZoom: true,
            zoomToRefPoint: true,
            flickEnabled: true,
            flickMinSpeed: 120,
            flickMomentum: 0.25,
            pinchRotate: false,
          },
          zoomPerClick: 2,
          zoomPerScroll: 1.2,
          zoomPerSecond: 1,
          blendTime: 0,
          alwaysBlend: false,
          autoHideControls: true,
          immediateRender: false,
          minZoomImageRatio: 0.9,
          maxZoomPixelRatio: 1.1,
          smoothTileEdgesMinZoom: 1.1,
          iOSDevice: n(),
          pixelsPerWheelLine: 40,
          pixelsPerArrowPress: 40,
          autoResize: true,
          preserveImageSizeOnResize: false,
          minScrollDeltaTime: 50,
          rotationIncrement: 90,
          showSequenceControl: true,
          sequenceControlAnchor: null,
          preserveViewport: false,
          preserveOverlays: false,
          navPrevNextWrap: false,
          showNavigationControl: true,
          navigationControlAnchor: null,
          showZoomControl: true,
          showHomeControl: true,
          showFullPageControl: true,
          showRotationControl: false,
          showFlipControl: false,
          controlsFadeDelay: 2e3,
          controlsFadeLength: 1500,
          mouseNavEnabled: true,
          showNavigator: false,
          navigatorId: null,
          navigatorPosition: null,
          navigatorSizeRatio: 0.2,
          navigatorMaintainSizeRatio: false,
          navigatorTop: null,
          navigatorLeft: null,
          navigatorHeight: null,
          navigatorWidth: null,
          navigatorAutoResize: true,
          navigatorAutoFade: true,
          navigatorRotate: true,
          navigatorBackground: '#000',
          navigatorOpacity: 0.8,
          navigatorBorderColor: '#555',
          navigatorDisplayRegionColor: '#900',
          degrees: 0,
          flipped: false,
          opacity: 1,
          preload: false,
          compositeOperation: null,
          imageSmoothingEnabled: true,
          placeholderFillStyle: null,
          showReferenceStrip: false,
          referenceStripScroll: 'horizontal',
          referenceStripElement: null,
          referenceStripHeight: null,
          referenceStripWidth: null,
          referenceStripPosition: 'BOTTOM_LEFT',
          referenceStripSizeRatio: 0.2,
          collectionRows: 3,
          collectionColumns: 0,
          collectionLayout: 'horizontal',
          collectionMode: false,
          collectionTileSize: 800,
          collectionTileMargin: 80,
          imageLoaderLimit: 0,
          maxImageCacheCount: 200,
          timeout: 3e4,
          useCanvas: true,
          prefixUrl: '/images/',
          navImages: {
            zoomIn: {
              REST: 'zoomin_rest.png',
              GROUP: 'zoomin_grouphover.png',
              HOVER: 'zoomin_hover.png',
              DOWN: 'zoomin_pressed.png',
            },
            zoomOut: {
              REST: 'zoomout_rest.png',
              GROUP: 'zoomout_grouphover.png',
              HOVER: 'zoomout_hover.png',
              DOWN: 'zoomout_pressed.png',
            },
            home: {
              REST: 'home_rest.png',
              GROUP: 'home_grouphover.png',
              HOVER: 'home_hover.png',
              DOWN: 'home_pressed.png',
            },
            fullpage: {
              REST: 'fullpage_rest.png',
              GROUP: 'fullpage_grouphover.png',
              HOVER: 'fullpage_hover.png',
              DOWN: 'fullpage_pressed.png',
            },
            rotateleft: {
              REST: 'rotateleft_rest.png',
              GROUP: 'rotateleft_grouphover.png',
              HOVER: 'rotateleft_hover.png',
              DOWN: 'rotateleft_pressed.png',
            },
            rotateright: {
              REST: 'rotateright_rest.png',
              GROUP: 'rotateright_grouphover.png',
              HOVER: 'rotateright_hover.png',
              DOWN: 'rotateright_pressed.png',
            },
            flip: {
              REST: 'flip_rest.png',
              GROUP: 'flip_grouphover.png',
              HOVER: 'flip_hover.png',
              DOWN: 'flip_pressed.png',
            },
            previous: {
              REST: 'previous_rest.png',
              GROUP: 'previous_grouphover.png',
              HOVER: 'previous_hover.png',
              DOWN: 'previous_pressed.png',
            },
            next: {
              REST: 'next_rest.png',
              GROUP: 'next_grouphover.png',
              HOVER: 'next_hover.png',
              DOWN: 'next_pressed.png',
            },
          },
          debugMode: false,
          debugGridColor: [
            '#437AB2',
            '#1B9E77',
            '#D95F02',
            '#7570B3',
            '#E7298A',
            '#66A61E',
            '#E6AB02',
            '#A6761D',
            '#666666',
          ],
        },
        SIGNAL: '----seadragon----',
        delegate: function (l, u) {
          return function () {
            var c = arguments;
            if (c === void 0) {
              c = [];
            }
            return u.apply(l, c);
          };
        },
        BROWSERS: {
          UNKNOWN: 0,
          IE: 1,
          FIREFOX: 2,
          SAFARI: 3,
          CHROME: 4,
          OPERA: 5,
          EDGE: 6,
          CHROMEEDGE: 7,
        },
        _viewers: new Map(),
        getViewer: function (l) {
          return e._viewers.get(this.getElement(l));
        },
        getElement: function (l) {
          if (typeof l == 'string') {
            l = document.getElementById(l);
          }
          return l;
        },
        getElementPosition: function (l) {
          var u = new e.Point();
          l = e.getElement(l);
          var c = e.getElementStyle(l).position === 'fixed';
          for (var h = a(l, c); h; ) {
            u.x += l.offsetLeft;
            u.y += l.offsetTop;
            if (c) {
              u = u.plus(e.getPageScroll());
            }
            l = h;
            c = e.getElementStyle(l).position === 'fixed';
            h = a(l, c);
          }
          return u;
        },
        getElementOffset: function (l) {
          l = e.getElement(l);
          var u = l && l.ownerDocument;
          var c;
          var h;
          var d = { top: 0, left: 0 };
          if (u) {
            c = u.documentElement;
            if (typeof l.getBoundingClientRect != 'undefined') {
              d = l.getBoundingClientRect();
            }
            h =
              u === u.window
                ? u
                : u.nodeType === 9
                ? u.defaultView || u.parentWindow
                : false;
            return new e.Point(
              d.left + (h.pageXOffset || c.scrollLeft) - (c.clientLeft || 0),
              d.top + (h.pageYOffset || c.scrollTop) - (c.clientTop || 0)
            );
          } else {
            return new e.Point();
          }
        },
        getElementSize: function (l) {
          l = e.getElement(l);
          return new e.Point(l.clientWidth, l.clientHeight);
        },
        getElementStyle: document.documentElement.currentStyle
          ? function (l) {
              l = e.getElement(l);
              return l.currentStyle;
            }
          : function (l) {
              l = e.getElement(l);
              return window.getComputedStyle(l, '');
            },
        getCssPropertyWithVendorPrefix: function (l) {
          var u = {};
          e.getCssPropertyWithVendorPrefix = function (c) {
            if (u[c] !== void 0) {
              return u[c];
            }
            var h = document.createElement('div').style;
            var d = null;
            if (h[c] === void 0) {
              var g = ['Webkit', 'Moz', 'MS', 'O', 'webkit', 'moz', 'ms', 'o'];
              var y = e.capitalizeFirstLetter(c);
              for (var x = 0; x < g.length; x++) {
                var b = g[x] + y;
                if (h[b] !== void 0) {
                  d = b;
                  break;
                }
              }
            } else {
              d = c;
            }
            u[c] = d;
            return d;
          };
          return e.getCssPropertyWithVendorPrefix(l);
        },
        capitalizeFirstLetter: function (l) {
          return l.charAt(0).toUpperCase() + l.slice(1);
        },
        positiveModulo: function (l, u) {
          var c = l % u;
          if (c < 0) {
            c += u;
          }
          return c;
        },
        pointInElement: function (l, u) {
          l = e.getElement(l);
          var c = e.getElementOffset(l);
          var h = e.getElementSize(l);
          return u.x >= c.x && u.x < c.x + h.x && u.y < c.y + h.y && u.y >= c.y;
        },
        getMousePosition: function (l) {
          if (typeof l.pageX == 'number') {
            e.getMousePosition = function (u) {
              var c = new e.Point();
              c.x = u.pageX;
              c.y = u.pageY;
              return c;
            };
          } else if (typeof l.clientX == 'number') {
            e.getMousePosition = function (u) {
              var c = new e.Point();
              c.x =
                u.clientX +
                document.body.scrollLeft +
                document.documentElement.scrollLeft;
              c.y =
                u.clientY +
                document.body.scrollTop +
                document.documentElement.scrollTop;
              return c;
            };
          } else {
            throw new Error(
              'Unknown event mouse position, no known technique.'
            );
          }
          return e.getMousePosition(l);
        },
        getPageScroll: function () {
          var l = document.documentElement || {};
          var u = document.body || {};
          if (typeof window.pageXOffset == 'number') {
            e.getPageScroll = function () {
              return new e.Point(window.pageXOffset, window.pageYOffset);
            };
          } else if (u.scrollLeft || u.scrollTop) {
            e.getPageScroll = function () {
              return new e.Point(
                document.body.scrollLeft,
                document.body.scrollTop
              );
            };
          } else if (l.scrollLeft || l.scrollTop) {
            e.getPageScroll = function () {
              return new e.Point(
                document.documentElement.scrollLeft,
                document.documentElement.scrollTop
              );
            };
          } else {
            return new e.Point(0, 0);
          }
          return e.getPageScroll();
        },
        setPageScroll: function (l) {
          if (typeof window.scrollTo == 'undefined') {
            var u = e.getPageScroll();
            if (u.x === l.x && u.y === l.y) {
              return;
            }
            document.body.scrollLeft = l.x;
            document.body.scrollTop = l.y;
            var c = e.getPageScroll();
            if (c.x !== u.x && c.y !== u.y) {
              e.setPageScroll = function (h) {
                document.body.scrollLeft = h.x;
                document.body.scrollTop = h.y;
              };
              return;
            }
            document.documentElement.scrollLeft = l.x;
            document.documentElement.scrollTop = l.y;
            c = e.getPageScroll();
            if (c.x !== u.x && c.y !== u.y) {
              e.setPageScroll = function (h) {
                document.documentElement.scrollLeft = h.x;
                document.documentElement.scrollTop = h.y;
              };
              return;
            }
            e.setPageScroll = function (h) {};
          } else {
            e.setPageScroll = function (h) {
              window.scrollTo(h.x, h.y);
            };
          }
          e.setPageScroll(l);
        },
        getWindowSize: function () {
          var l = document.documentElement || {};
          var u = document.body || {};
          if (typeof window.innerWidth == 'number') {
            e.getWindowSize = function () {
              return new e.Point(window.innerWidth, window.innerHeight);
            };
          } else if (l.clientWidth || l.clientHeight) {
            e.getWindowSize = function () {
              return new e.Point(
                document.documentElement.clientWidth,
                document.documentElement.clientHeight
              );
            };
          } else if (u.clientWidth || u.clientHeight) {
            e.getWindowSize = function () {
              return new e.Point(
                document.body.clientWidth,
                document.body.clientHeight
              );
            };
          } else {
            throw new Error('Unknown window size, no known technique.');
          }
          return e.getWindowSize();
        },
        makeCenteredNode: function (l) {
          l = e.getElement(l);
          var u = [
            e.makeNeutralElement('div'),
            e.makeNeutralElement('div'),
            e.makeNeutralElement('div'),
          ];
          e.extend(u[0].style, {
            display: 'table',
            height: '100%',
            width: '100%',
          });
          e.extend(u[1].style, { display: 'table-row' });
          e.extend(u[2].style, {
            display: 'table-cell',
            verticalAlign: 'middle',
            textAlign: 'center',
          });
          u[0].appendChild(u[1]);
          u[1].appendChild(u[2]);
          u[2].appendChild(l);
          return u[0];
        },
        makeNeutralElement: function (l) {
          var u = document.createElement(l);
          var c = u.style;
          c.background = 'transparent none';
          c.border = 'none';
          c.margin = '0px';
          c.padding = '0px';
          c.position = 'static';
          return u;
        },
        now: function () {
          if (Date.now) {
            e.now = Date.now;
          } else {
            e.now = function () {
              return new Date().getTime();
            };
          }
          return e.now();
        },
        makeTransparentImage: function (l) {
          var u = e.makeNeutralElement('img');
          u.src = l;
          return u;
        },
        setElementOpacity: function (l, u, c) {
          var h;
          var d;
          l = e.getElement(l);
          if (c && !e.Browser.alpha) {
            u = Math.round(u);
          }
          if (e.Browser.opacity) {
            l.style.opacity = u < 1 ? u : '';
          } else if (u < 1) {
            h = Math.round(100 * u);
            d = 'alpha(opacity=' + h + ')';
            l.style.filter = d;
          } else {
            l.style.filter = '';
          }
        },
        setElementTouchActionNone: function (l) {
          l = e.getElement(l);
          if (typeof l.style.touchAction == 'undefined') {
            if (typeof l.style.msTouchAction != 'undefined') {
              l.style.msTouchAction = 'none';
            }
          } else {
            l.style.touchAction = 'none';
          }
        },
        setElementPointerEvents: function (l, u) {
          l = e.getElement(l);
          if (typeof l.style.pointerEvents != 'undefined') {
            l.style.pointerEvents = u;
          }
        },
        setElementPointerEventsNone: function (l) {
          e.setElementPointerEvents(l, 'none');
        },
        addClass: function (l, u) {
          l = e.getElement(l);
          if (l.className) {
            if ((' ' + l.className + ' ').indexOf(' ' + u + ' ') === -1) {
              l.className += ' ' + u;
            }
          } else {
            l.className = u;
          }
        },
        indexOf: function (l, u, c) {
          if (Array.prototype.indexOf) {
            this.indexOf = function (h, d, g) {
              return h.indexOf(d, g);
            };
          } else {
            this.indexOf = function (h, d, g) {
              var x = g || 0;
              if (!h) {
                throw new TypeError();
              }
              var b = h.length;
              if (b === 0 || x >= b) {
                return -1;
              }
              if (x < 0) {
                x = b - Math.abs(x);
              }
              for (var y = x; y < b; y++) {
                if (h[y] === d) {
                  return y;
                }
              }
              return -1;
            };
          }
          return this.indexOf(l, u, c);
        },
        removeClass: function (l, u) {
          var h = [];
          l = e.getElement(l);
          var c = l.className.split(/\s+/);
          for (var d = 0; d < c.length; d++) {
            if (c[d] && c[d] !== u) {
              h.push(c[d]);
            }
          }
          l.className = h.join(' ');
        },
        normalizeEventListenerOptions: function (l) {
          var u;
          if (typeof l == 'undefined') {
            u = e.supportsEventListenerOptions ? { capture: false } : false;
          } else if (typeof l == 'boolean') {
            u = e.supportsEventListenerOptions ? { capture: l } : l;
          } else {
            u = e.supportsEventListenerOptions
              ? l
              : typeof l.capture != 'undefined'
              ? l.capture
              : false;
          }
          return u;
        },
        addEvent: (function () {
          if (e.supportsAddEventListener) {
            return function (l, u, c, h) {
              h = e.normalizeEventListenerOptions(h);
              l = e.getElement(l);
              l.addEventListener(u, c, h);
            };
          }
          if (document.documentElement.attachEvent && document.attachEvent) {
            return function (l, u, c) {
              l = e.getElement(l);
              l.attachEvent('on' + u, c);
            };
          }
          throw new Error('No known event model.');
        })(),
        removeEvent: (function () {
          if (e.supportsRemoveEventListener) {
            return function (l, u, c, h) {
              h = e.normalizeEventListenerOptions(h);
              l = e.getElement(l);
              l.removeEventListener(u, c, h);
            };
          }
          if (document.documentElement.detachEvent && document.detachEvent) {
            return function (l, u, c) {
              l = e.getElement(l);
              l.detachEvent('on' + u, c);
            };
          }
          throw new Error('No known event model.');
        })(),
        cancelEvent: function (l) {
          l.preventDefault();
        },
        eventIsCanceled: function (l) {
          return l.defaultPrevented;
        },
        stopEvent: function (l) {
          l.stopPropagation();
        },
        createCallback: function (l, u) {
          var c = [];
          for (var h = 2; h < arguments.length; h++) {
            c.push(arguments[h]);
          }
          return function () {
            var d = c.concat([]);
            for (var g = 0; g < arguments.length; g++) {
              d.push(arguments[g]);
            }
            return u.apply(l, d);
          };
        },
        getUrlParameter: function (l) {
          var u = s[l];
          return u || null;
        },
        getUrlProtocol: function (l) {
          var u = l.match(/^([a-z]+:)\/\//i);
          if (u === null) {
            return window.location.protocol;
          } else {
            return u[1].toLowerCase();
          }
        },
        createAjaxRequest: function (l) {
          var u;
          try {
            u = !!new ActiveXObject('Microsoft.XMLHTTP');
          } catch {
            u = false;
          }
          if (u) {
            if (window.XMLHttpRequest) {
              e.createAjaxRequest = function (c) {
                if (c) {
                  return new ActiveXObject('Microsoft.XMLHTTP');
                } else {
                  return new XMLHttpRequest();
                }
              };
            } else {
              e.createAjaxRequest = function () {
                return new ActiveXObject('Microsoft.XMLHTTP');
              };
            }
          } else if (window.XMLHttpRequest) {
            e.createAjaxRequest = function () {
              return new XMLHttpRequest();
            };
          } else {
            throw new Error("Browser doesn't support XMLHttpRequest.");
          }
          return e.createAjaxRequest(l);
        },
        makeAjaxRequest: function (l, u, c) {
          var h;
          var d;
          var g;
          if (e.isPlainObject(l)) {
            u = l.success;
            c = l.error;
            h = l.withCredentials;
            d = l.headers;
            g = l.responseType || null;
            l = l.url;
          }
          var y = e.getUrlProtocol(l);
          var x = e.createAjaxRequest(y === 'file:');
          if (!e.isFunction(u)) {
            throw new Error('makeAjaxRequest requires a success callback');
          }
          x.onreadystatechange = function () {
            if (x.readyState === 4) {
              x.onreadystatechange = function () {};
              if (
                (x.status >= 200 && x.status < 300) ||
                (x.status === 0 && y !== 'http:' && y !== 'https:')
              ) {
                u(x);
              } else {
                e.console.log('AJAX request returned %d: %s', x.status, l);
                if (e.isFunction(c)) {
                  c(x);
                }
              }
            }
          };
          try {
            x.open('GET', l, true);
            if (g) {
              x.responseType = g;
            }
            if (d) {
              for (var b in d) {
                if (Object.prototype.hasOwnProperty.call(d, b) && d[b]) {
                  x.setRequestHeader(b, d[b]);
                }
              }
            }
            if (h) {
              x.withCredentials = true;
            }
            x.send(null);
          } catch (T) {
            e.console.log(
              '%s while making AJAX request: %s',
              T.name,
              T.message
            );
            x.onreadystatechange = function () {};
            if (e.isFunction(c)) {
              c(x, T);
            }
          }
          return x;
        },
        jsonp: function (l) {
          var c = l.url;
          var h =
            document.head ||
            document.getElementsByTagName('head')[0] ||
            document.documentElement;
          var d = l.callbackName || 'openseadragon' + e.now();
          var g = window[d];
          var y = '$1' + d + '$2';
          var x = l.param || 'callback';
          var b = l.callback;
          c = c.replace(/(=)\?(&|$)|\?\?/i, y);
          c += (/\?/.test(c) ? '&' : '?') + x + '=' + d;
          window[d] = function (T) {
            if (g) {
              window[d] = g;
            } else {
              try {
                delete window[d];
              } catch {}
            }
            if (b && e.isFunction(b)) {
              b(T);
            }
          };
          var u = document.createElement('script');
          if (l.async !== void 0 || l.async !== false) {
            u.async = 'async';
          }
          if (l.scriptCharset) {
            u.charset = l.scriptCharset;
          }
          u.src = c;
          u.onload = u.onreadystatechange = function (T, f) {
            if (f || !u.readyState || /loaded|complete/.test(u.readyState)) {
              u.onload = u.onreadystatechange = null;
              if (h && u.parentNode) {
                h.removeChild(u);
              }
              u = void 0;
            }
          };
          h.insertBefore(u, h.firstChild);
        },
        createFromDZI: function () {
          throw 'OpenSeadragon.createFromDZI is deprecated, use Viewer.open.';
        },
        parseXml: function (l) {
          if (window.DOMParser) {
            e.parseXml = function (u) {
              var c = null;
              var h = new DOMParser();
              c = h.parseFromString(u, 'text/xml');
              return c;
            };
          } else if (window.ActiveXObject) {
            e.parseXml = function (u) {
              var c = null;
              c = new ActiveXObject('Microsoft.XMLDOM');
              c.async = false;
              c.loadXML(u);
              return c;
            };
          } else {
            throw new Error("Browser doesn't support XML DOM.");
          }
          return e.parseXml(l);
        },
        parseJSON: function (l) {
          e.parseJSON = window.JSON.parse;
          return e.parseJSON(l);
        },
        imageFormatSupported: function (l) {
          l = l || '';
          return !!o[l.toLowerCase()];
        },
        setImageFormatsSupported: function (l) {
          e.extend(o, l);
        },
      });
      var r = function (l) {};
      e.console = window.console || {
        log: r,
        debug: r,
        info: r,
        warn: r,
        error: r,
        assert: r,
      };
      e.Browser = { vendor: e.BROWSERS.UNKNOWN, version: 0, alpha: true };
      var o = {
        bmp: false,
        jpeg: true,
        jpg: true,
        png: true,
        tif: false,
        wdp: false,
      };
      var s = {};
      (function () {
        var l = navigator.appVersion;
        var u = navigator.userAgent;
        var c;
        switch (navigator.appName) {
          case 'Microsoft Internet Explorer':
            if (!!window.attachEvent && !!window.ActiveXObject) {
              e.Browser.vendor = e.BROWSERS.IE;
              e.Browser.version = parseFloat(
                u.substring(
                  u.indexOf('MSIE') + 5,
                  u.indexOf(';', u.indexOf('MSIE'))
                )
              );
            }
            break;
          case 'Netscape':
            if (window.addEventListener) {
              if (u.indexOf('Edge') >= 0) {
                e.Browser.vendor = e.BROWSERS.EDGE;
                e.Browser.version = parseFloat(
                  u.substring(u.indexOf('Edge') + 5)
                );
              } else if (u.indexOf('Edg') >= 0) {
                e.Browser.vendor = e.BROWSERS.CHROMEEDGE;
                e.Browser.version = parseFloat(
                  u.substring(u.indexOf('Edg') + 4)
                );
              } else if (u.indexOf('Firefox') >= 0) {
                e.Browser.vendor = e.BROWSERS.FIREFOX;
                e.Browser.version = parseFloat(
                  u.substring(u.indexOf('Firefox') + 8)
                );
              } else if (u.indexOf('Safari') >= 0) {
                e.Browser.vendor =
                  u.indexOf('Chrome') >= 0
                    ? e.BROWSERS.CHROME
                    : e.BROWSERS.SAFARI;
                e.Browser.version = parseFloat(
                  u.substring(
                    u.substring(0, u.indexOf('Safari')).lastIndexOf('/') + 1,
                    u.indexOf('Safari')
                  )
                );
              } else {
                c = new RegExp('Trident/.*rv:([0-9]{1,}[.0-9]{0,})');
                if (c.exec(u) !== null) {
                  e.Browser.vendor = e.BROWSERS.IE;
                  e.Browser.version = parseFloat(RegExp.$1);
                }
              }
            }
            break;
          case 'Opera':
            e.Browser.vendor = e.BROWSERS.OPERA;
            e.Browser.version = parseFloat(l);
            break;
        }
        var h = window.location.search.substring(1);
        var d = h.split('&');
        var g;
        var y;
        for (var x = 0; x < d.length; x++) {
          g = d[x];
          y = g.indexOf('=');
          if (y > 0) {
            var b = g.substring(0, y);
            var T = g.substring(y + 1);
            try {
              s[b] = decodeURIComponent(T);
            } catch {
              e.console.error('Ignoring malformed URL parameter: %s=%s', b, T);
            }
          }
        }
        e.Browser.alpha =
          e.Browser.vendor !== e.BROWSERS.CHROME || !(e.Browser.version < 2);
        e.Browser.opacity = true;
        if (e.Browser.vendor === e.BROWSERS.IE && e.Browser.version < 11) {
          e.console.error(
            'Internet Explorer versions < 11 are not supported by OpenSeadragon'
          );
        }
      })();
      (function () {
        var l = window;
        var u =
          l.requestAnimationFrame ||
          l.mozRequestAnimationFrame ||
          l.webkitRequestAnimationFrame ||
          l.msRequestAnimationFrame;
        var c =
          l.cancelAnimationFrame ||
          l.mozCancelAnimationFrame ||
          l.webkitCancelAnimationFrame ||
          l.msCancelAnimationFrame;
        if (u && c) {
          e.requestAnimationFrame = function () {
            return u.apply(l, arguments);
          };
          e.cancelAnimationFrame = function () {
            return c.apply(l, arguments);
          };
        } else {
          var h = [];
          var d = [];
          var g = 0;
          var y;
          e.requestAnimationFrame = function (x) {
            h.push([++g, x]);
            if (!y) {
              y = setInterval(function () {
                if (h.length) {
                  var b = e.now();
                  var T = d;
                  d = h;
                  for (h = T; d.length; ) {
                    d.shift()[1](b);
                  }
                } else {
                  clearInterval(y);
                  y = void 0;
                }
              }, 1e3 / 50);
            }
            return g;
          };
          e.cancelAnimationFrame = function (x) {
            var b = 0;
            for (var T = h.length; b < T; b += 1) {
              if (h[b][0] === x) {
                h.splice(b, 1);
                return;
              }
            }
            b = 0;
            for (T = d.length; b < T; b += 1) {
              if (d[b][0] === x) {
                d.splice(b, 1);
                return;
              }
            }
          };
        }
      })();
    })();
    (function () {
      var e = xt;
      var n = function () {
        return t;
      };
      if (i.exports) {
        i.exports = n();
      } else {
        e.OpenSeadragon = n();
      }
    })();
    (function () {
      var e = t;
      var n = {
        supportsFullScreen: false,
        isFullScreen: function () {
          return false;
        },
        getFullScreenElement: function () {
          return null;
        },
        requestFullScreen: function () {},
        exitFullScreen: function () {},
        cancelFullScreen: function () {},
        fullScreenEventName: '',
        fullScreenErrorEventName: '',
      };
      if (document.exitFullscreen) {
        n.supportsFullScreen = true;
        n.getFullScreenElement = function () {
          return document.fullscreenElement;
        };
        n.requestFullScreen = function (r) {
          return r.requestFullscreen();
        };
        n.exitFullScreen = function () {
          document.exitFullscreen();
        };
        n.fullScreenEventName = 'fullscreenchange';
        n.fullScreenErrorEventName = 'fullscreenerror';
      } else if (document.msExitFullscreen) {
        n.supportsFullScreen = true;
        n.getFullScreenElement = function () {
          return document.msFullscreenElement;
        };
        n.requestFullScreen = function (r) {
          return r.msRequestFullscreen();
        };
        n.exitFullScreen = function () {
          document.msExitFullscreen();
        };
        n.fullScreenEventName = 'MSFullscreenChange';
        n.fullScreenErrorEventName = 'MSFullscreenError';
      } else if (document.webkitExitFullscreen) {
        n.supportsFullScreen = true;
        n.getFullScreenElement = function () {
          return document.webkitFullscreenElement;
        };
        n.requestFullScreen = function (r) {
          return r.webkitRequestFullscreen();
        };
        n.exitFullScreen = function () {
          document.webkitExitFullscreen();
        };
        n.fullScreenEventName = 'webkitfullscreenchange';
        n.fullScreenErrorEventName = 'webkitfullscreenerror';
      } else if (document.webkitCancelFullScreen) {
        n.supportsFullScreen = true;
        n.getFullScreenElement = function () {
          return document.webkitCurrentFullScreenElement;
        };
        n.requestFullScreen = function (r) {
          return r.webkitRequestFullScreen();
        };
        n.exitFullScreen = function () {
          document.webkitCancelFullScreen();
        };
        n.fullScreenEventName = 'webkitfullscreenchange';
        n.fullScreenErrorEventName = 'webkitfullscreenerror';
      } else if (document.mozCancelFullScreen) {
        n.supportsFullScreen = true;
        n.getFullScreenElement = function () {
          return document.mozFullScreenElement;
        };
        n.requestFullScreen = function (r) {
          return r.mozRequestFullScreen();
        };
        n.exitFullScreen = function () {
          document.mozCancelFullScreen();
        };
        n.fullScreenEventName = 'mozfullscreenchange';
        n.fullScreenErrorEventName = 'mozfullscreenerror';
      }
      n.isFullScreen = function () {
        return n.getFullScreenElement() !== null;
      };
      n.cancelFullScreen = function () {
        e.console.error(
          'cancelFullScreen is deprecated. Use exitFullScreen instead.'
        );
        n.exitFullScreen();
      };
      e.extend(e, n);
    })();
    (function () {
      var e = t;
      e.EventSource = function () {
        this.events = {};
      };
      e.EventSource.prototype = {
        addOnceHandler: function (n, r, o, s) {
          var a = this;
          s = s || 1;
          var l = 0;
          var u = function (c) {
            l++;
            if (l === s) {
              a.removeHandler(n, u);
            }
            r(c);
          };
          this.addHandler(n, u, o);
        },
        addHandler: function (n, r, o) {
          var s = this.events[n];
          if (!s) {
            this.events[n] = s = [];
          }
          if (r && e.isFunction(r)) {
            s[s.length] = { handler: r, userData: o || null };
          }
        },
        removeHandler: function (n, r) {
          var o = this.events[n];
          var s = [];
          var a;
          if (!!o && e.isArray(o)) {
            for (a = 0; a < o.length; a++) {
              if (o[a].handler !== r) {
                s.push(o[a]);
              }
            }
            this.events[n] = s;
          }
        },
        removeAllHandlers: function (n) {
          if (n) {
            this.events[n] = [];
          } else {
            for (var r in this.events) {
              this.events[r] = [];
            }
          }
        },
        getHandler: function (n) {
          var r = this.events[n];
          if (!r || !r.length) {
            return null;
          } else {
            r = r.length === 1 ? [r[0]] : Array.apply(null, r);
            return function (o, s) {
              var l = r.length;
              for (var a = 0; a < l; a++) {
                if (r[a]) {
                  s.eventSource = o;
                  s.userData = r[a].userData;
                  r[a].handler(s);
                }
              }
            };
          }
        },
        raiseEvent: function (n, r) {
          var o = this.getHandler(n);
          if (o) {
            if (!r) {
              r = {};
            }
            o(this, r);
          }
        },
      };
    })();
    (function () {
      function o(v) {
        try {
          return v.addEventListener && v.removeEventListener;
        } catch {
          return false;
        }
      }
      function s(v) {
        var m = n[v.hash];
        var S;
        var M;
        var H;
        var ee;
        var xe = m.activePointersLists.length;
        for (var w = 0; w < xe; w++) {
          M = m.activePointersLists[w];
          if (M.getLength() > 0) {
            ee = [];
            H = M.asArray();
            for (S = 0; S < H.length; S++) {
              ee.push(H[S]);
            }
            for (S = 0; S < ee.length; S++) {
              R(v, M, ee[S]);
            }
          }
        }
        for (w = 0; w < xe; w++) {
          m.activePointersLists.pop();
        }
        m.sentDragEvent = false;
      }
      function a(v) {
        var m = n[v.hash];
        var w;
        var S;
        if (!m.tracking) {
          for (S = 0; S < e.MouseTracker.subscribeEvents.length; S++) {
            w = e.MouseTracker.subscribeEvents[S];
            e.addEvent(
              v.element,
              w,
              m[w],
              w === e.MouseTracker.wheelEventName
                ? { passive: false, capture: false }
                : false
            );
          }
          s(v);
          m.tracking = true;
        }
      }
      function l(v) {
        var m = n[v.hash];
        var w;
        var S;
        if (m.tracking) {
          for (S = 0; S < e.MouseTracker.subscribeEvents.length; S++) {
            w = e.MouseTracker.subscribeEvents[S];
            e.removeEvent(v.element, w, m[w], false);
          }
          s(v);
          m.tracking = false;
        }
      }
      function u(v, m) {
        var w = n[v.hash];
        if (m === 'pointerevent') {
          return {
            upName: 'pointerup',
            upHandler: w.pointerupcaptured,
            moveName: 'pointermove',
            moveHandler: w.pointermovecaptured,
          };
        }
        if (m === 'mouse') {
          return {
            upName: 'pointerup',
            upHandler: w.pointerupcaptured,
            moveName: 'pointermove',
            moveHandler: w.pointermovecaptured,
          };
        }
        if (m === 'touch') {
          return {
            upName: 'touchend',
            upHandler: w.touchendcaptured,
            moveName: 'touchmove',
            moveHandler: w.touchmovecaptured,
          };
        }
        throw new Error(
          'MouseTracker.getCaptureEventParams: Unknown pointer type.'
        );
      }
      function c(v, m) {
        var w;
        if (e.MouseTracker.havePointerCapture) {
          if (e.MouseTracker.havePointerEvents) {
            try {
              v.element.setPointerCapture(m.id);
            } catch {
              e.console.warn(
                'setPointerCapture() called on invalid pointer ID'
              );
              return;
            }
          } else {
            v.element.setCapture(true);
          }
        } else {
          w = u(v, e.MouseTracker.havePointerEvents ? 'pointerevent' : m.type);
          if (r && o(window.top)) {
            e.addEvent(window.top, w.upName, w.upHandler, true);
          }
          e.addEvent(
            e.MouseTracker.captureElement,
            w.upName,
            w.upHandler,
            true
          );
          e.addEvent(
            e.MouseTracker.captureElement,
            w.moveName,
            w.moveHandler,
            true
          );
        }
        J(v, m, true);
      }
      function h(v, m) {
        var w;
        var S;
        var M;
        if (e.MouseTracker.havePointerCapture) {
          if (e.MouseTracker.havePointerEvents) {
            S = v.getActivePointersListByType(m.type);
            M = S.getById(m.id);
            if (!M || !M.captured) {
              return;
            }
            try {
              v.element.releasePointerCapture(m.id);
            } catch {}
          } else {
            v.element.releaseCapture();
          }
        } else {
          w = u(v, e.MouseTracker.havePointerEvents ? 'pointerevent' : m.type);
          if (r && o(window.top)) {
            e.removeEvent(window.top, w.upName, w.upHandler, true);
          }
          e.removeEvent(
            e.MouseTracker.captureElement,
            w.moveName,
            w.moveHandler,
            true
          );
          e.removeEvent(
            e.MouseTracker.captureElement,
            w.upName,
            w.upHandler,
            true
          );
        }
        J(v, m, false);
      }
      function d(v) {
        if (e.MouseTracker.havePointerEvents) {
          return v.pointerId;
        } else {
          return e.MouseTracker.mousePointerId;
        }
      }
      function g(v) {
        if (e.MouseTracker.havePointerEvents) {
          return (
            v.pointerType || (e.Browser.vendor === e.BROWSERS.IE ? 'mouse' : '')
          );
        } else {
          return 'mouse';
        }
      }
      function y(v) {
        if (e.MouseTracker.havePointerEvents) {
          return v.isPrimary;
        } else {
          return true;
        }
      }
      function x(v) {
        return e.getMousePosition(v);
      }
      function b(v, m) {
        return T(x(v), m);
      }
      function T(v, m) {
        var w = e.getElementOffset(m);
        return v.minus(w);
      }
      function f(v, m) {
        return new e.Point((v.x + m.x) / 2, (v.y + m.y) / 2);
      }
      function E(v, m) {
        var w = {
          originalEvent: m,
          eventType: 'click',
          pointerType: 'mouse',
          isEmulated: false,
        };
        k(v, w);
        if (w.preventDefault && !w.defaultPrevented) {
          e.cancelEvent(m);
        }
        if (w.stopPropagation) {
          e.stopEvent(m);
        }
      }
      function A(v, m) {
        var w = {
          originalEvent: m,
          eventType: 'dblclick',
          pointerType: 'mouse',
          isEmulated: false,
        };
        k(v, w);
        if (w.preventDefault && !w.defaultPrevented) {
          e.cancelEvent(m);
        }
        if (w.stopPropagation) {
          e.stopEvent(m);
        }
      }
      function C(v, m) {
        var w = null;
        var S = {
          originalEvent: m,
          eventType: 'keydown',
          pointerType: '',
          isEmulated: false,
        };
        k(v, S);
        if (v.keyDownHandler && !S.preventGesture && !S.defaultPrevented) {
          w = {
            eventSource: v,
            keyCode: m.keyCode ? m.keyCode : m.charCode,
            ctrl: m.ctrlKey,
            shift: m.shiftKey,
            alt: m.altKey,
            meta: m.metaKey,
            originalEvent: m,
            preventDefault: S.preventDefault || S.defaultPrevented,
            userData: v.userData,
          };
          v.keyDownHandler(w);
        }
        if (
          (w && w.preventDefault) ||
          (S.preventDefault && !S.defaultPrevented)
        ) {
          e.cancelEvent(m);
        }
        if (S.stopPropagation) {
          e.stopEvent(m);
        }
      }
      function O(v, m) {
        var w = null;
        var S = {
          originalEvent: m,
          eventType: 'keyup',
          pointerType: '',
          isEmulated: false,
        };
        k(v, S);
        if (v.keyUpHandler && !S.preventGesture && !S.defaultPrevented) {
          w = {
            eventSource: v,
            keyCode: m.keyCode ? m.keyCode : m.charCode,
            ctrl: m.ctrlKey,
            shift: m.shiftKey,
            alt: m.altKey,
            meta: m.metaKey,
            originalEvent: m,
            preventDefault: S.preventDefault || S.defaultPrevented,
            userData: v.userData,
          };
          v.keyUpHandler(w);
        }
        if (
          (w && w.preventDefault) ||
          (S.preventDefault && !S.defaultPrevented)
        ) {
          e.cancelEvent(m);
        }
        if (S.stopPropagation) {
          e.stopEvent(m);
        }
      }
      function D(v, m) {
        var w = null;
        var S = {
          originalEvent: m,
          eventType: 'keypress',
          pointerType: '',
          isEmulated: false,
        };
        k(v, S);
        if (v.keyHandler && !S.preventGesture && !S.defaultPrevented) {
          w = {
            eventSource: v,
            keyCode: m.keyCode ? m.keyCode : m.charCode,
            ctrl: m.ctrlKey,
            shift: m.shiftKey,
            alt: m.altKey,
            meta: m.metaKey,
            originalEvent: m,
            preventDefault: S.preventDefault || S.defaultPrevented,
            userData: v.userData,
          };
          v.keyHandler(w);
        }
        if (
          (w && w.preventDefault) ||
          (S.preventDefault && !S.defaultPrevented)
        ) {
          e.cancelEvent(m);
        }
        if (S.stopPropagation) {
          e.stopEvent(m);
        }
      }
      function N(v, m) {
        var w = {
          originalEvent: m,
          eventType: 'focus',
          pointerType: '',
          isEmulated: false,
        };
        k(v, w);
        if (v.focusHandler && !w.preventGesture) {
          v.focusHandler({
            eventSource: v,
            originalEvent: m,
            userData: v.userData,
          });
        }
      }
      function B(v, m) {
        var w = {
          originalEvent: m,
          eventType: 'blur',
          pointerType: '',
          isEmulated: false,
        };
        k(v, w);
        if (v.blurHandler && !w.preventGesture) {
          v.blurHandler({
            eventSource: v,
            originalEvent: m,
            userData: v.userData,
          });
        }
      }
      function Z(v, m) {
        var w = null;
        var S = {
          originalEvent: m,
          eventType: 'contextmenu',
          pointerType: 'mouse',
          isEmulated: false,
        };
        k(v, S);
        if (v.contextMenuHandler && !S.preventGesture && !S.defaultPrevented) {
          w = {
            eventSource: v,
            position: T(x(m), v.element),
            originalEvent: S.originalEvent,
            preventDefault: S.preventDefault || S.defaultPrevented,
            userData: v.userData,
          };
          v.contextMenuHandler(w);
        }
        if (
          (w && w.preventDefault) ||
          (S.preventDefault && !S.defaultPrevented)
        ) {
          e.cancelEvent(m);
        }
        if (S.stopPropagation) {
          e.stopEvent(m);
        }
      }
      function Y(v, m) {
        K(v, m, m);
      }
      function U(v, m) {
        var w = {
          target: m.target || m.srcElement,
          type: 'wheel',
          shiftKey: m.shiftKey || false,
          clientX: m.clientX,
          clientY: m.clientY,
          pageX: m.pageX ? m.pageX : m.clientX,
          pageY: m.pageY ? m.pageY : m.clientY,
          deltaMode: m.type === 'MozMousePixelScroll' ? 0 : 1,
          deltaX: 0,
          deltaZ: 0,
        };
        if (e.MouseTracker.wheelEventName === 'mousewheel') {
          w.deltaY = -m.wheelDelta / e.DEFAULT_SETTINGS.pixelsPerWheelLine;
        } else {
          w.deltaY = m.detail;
        }
        K(v, w, m);
      }
      function K(v, m, w) {
        var S = 0;
        var H = null;
        S = m.deltaY < 0 ? 1 : -1;
        var M = {
          originalEvent: m,
          eventType: 'wheel',
          pointerType: 'mouse',
          isEmulated: m !== w,
        };
        k(v, M);
        if (v.scrollHandler && !M.preventGesture && !M.defaultPrevented) {
          H = {
            eventSource: v,
            pointerType: 'mouse',
            position: b(m, v.element),
            scroll: S,
            shift: m.shiftKey,
            isTouchEvent: false,
            originalEvent: w,
            preventDefault: M.preventDefault || M.defaultPrevented,
            userData: v.userData,
          };
          v.scrollHandler(H);
        }
        if (M.stopPropagation) {
          e.stopEvent(w);
        }
        if (
          (H && H.preventDefault) ||
          (M.preventDefault && !M.defaultPrevented)
        ) {
          e.cancelEvent(w);
        }
      }
      function Q(v, m) {
        var w = { id: e.MouseTracker.mousePointerId, type: 'mouse' };
        var S = {
          originalEvent: m,
          eventType: 'lostpointercapture',
          pointerType: 'mouse',
          isEmulated: false,
        };
        k(v, S);
        if (m.target === v.element) {
          J(v, w, false);
        }
        if (S.stopPropagation) {
          e.stopEvent(m);
        }
      }
      function le(v, m) {
        var M = m.changedTouches.length;
        var H;
        var ee = v.getActivePointersListByType('touch');
        var w = e.now();
        if (ee.getLength() > m.touches.length - M) {
          e.console.warn(
            "Tracked touch contact count doesn't match event.touches.length"
          );
        }
        var xe = {
          originalEvent: m,
          eventType: 'pointerdown',
          pointerType: 'touch',
          isEmulated: false,
        };
        k(v, xe);
        for (var S = 0; S < M; S++) {
          H = {
            id: m.changedTouches[S].identifier,
            type: 'touch',
            isPrimary: ee.getLength() === 0,
            currentPos: x(m.changedTouches[S]),
            currentTime: w,
          };
          X(v, xe, H);
          oe(v, xe, H, 0);
          J(v, H, true);
        }
        if (xe.preventDefault && !xe.defaultPrevented) {
          e.cancelEvent(m);
        }
        if (xe.stopPropagation) {
          e.stopEvent(m);
        }
      }
      function re(v, m) {
        var M = m.changedTouches.length;
        var H;
        var w = e.now();
        var ee = {
          originalEvent: m,
          eventType: 'pointerup',
          pointerType: 'touch',
          isEmulated: false,
        };
        k(v, ee);
        for (var S = 0; S < M; S++) {
          H = {
            id: m.changedTouches[S].identifier,
            type: 'touch',
            currentPos: x(m.changedTouches[S]),
            currentTime: w,
          };
          Ce(v, ee, H, 0);
          J(v, H, false);
          pe(v, ee, H);
        }
        if (ee.preventDefault && !ee.defaultPrevented) {
          e.cancelEvent(m);
        }
        if (ee.stopPropagation) {
          e.stopEvent(m);
        }
      }
      function se(v, m) {
        var M = m.changedTouches.length;
        var H;
        var w = e.now();
        var ee = {
          originalEvent: m,
          eventType: 'pointermove',
          pointerType: 'touch',
          isEmulated: false,
        };
        k(v, ee);
        for (var S = 0; S < M; S++) {
          H = {
            id: m.changedTouches[S].identifier,
            type: 'touch',
            currentPos: x(m.changedTouches[S]),
            currentTime: w,
          };
          he(v, ee, H);
        }
        if (ee.preventDefault && !ee.defaultPrevented) {
          e.cancelEvent(m);
        }
        if (ee.stopPropagation) {
          e.stopEvent(m);
        }
      }
      function fe(v, m) {
        var w = m.changedTouches.length;
        var M;
        var H = {
          originalEvent: m,
          eventType: 'pointercancel',
          pointerType: 'touch',
          isEmulated: false,
        };
        k(v, H);
        for (var S = 0; S < w; S++) {
          M = { id: m.changedTouches[S].identifier, type: 'touch' };
          He(v, H, M);
        }
        if (H.stopPropagation) {
          e.stopEvent(m);
        }
      }
      function me(v, m) {
        if (!e.eventIsCanceled(m)) {
          m.preventDefault();
        }
        return false;
      }
      function q(v, m) {
        if (!e.eventIsCanceled(m)) {
          m.preventDefault();
        }
        return false;
      }
      function Le(v, m) {
        var w = {
          originalEvent: m,
          eventType: 'gotpointercapture',
          pointerType: g(m),
          isEmulated: false,
        };
        k(v, w);
        if (m.target === v.element) {
          J(v, { id: m.pointerId, type: g(m) }, true);
        }
        if (w.stopPropagation) {
          e.stopEvent(m);
        }
      }
      function F(v, m) {
        var w = {
          originalEvent: m,
          eventType: 'lostpointercapture',
          pointerType: g(m),
          isEmulated: false,
        };
        k(v, w);
        if (m.target === v.element) {
          J(v, { id: m.pointerId, type: g(m) }, false);
        }
        if (w.stopPropagation) {
          e.stopEvent(m);
        }
      }
      function V(v, m) {
        var w = {
          id: d(m),
          type: g(m),
          isPrimary: y(m),
          currentPos: x(m),
          currentTime: e.now(),
        };
        var S = {
          originalEvent: m,
          eventType: 'pointerenter',
          pointerType: w.type,
          isEmulated: false,
        };
        k(v, S);
        X(v, S, w);
      }
      function z(v, m) {
        var w = {
          id: d(m),
          type: g(m),
          isPrimary: y(m),
          currentPos: x(m),
          currentTime: e.now(),
        };
        var S = {
          originalEvent: m,
          eventType: 'pointerleave',
          pointerType: w.type,
          isEmulated: false,
        };
        k(v, S);
        pe(v, S, w);
      }
      function j(v, m) {
        var w = {
          id: d(m),
          type: g(m),
          isPrimary: y(m),
          currentPos: x(m),
          currentTime: e.now(),
        };
        var S = {
          originalEvent: m,
          eventType: 'pointerover',
          pointerType: w.type,
          isEmulated: false,
        };
        k(v, S);
        _e(v, S, w);
        if (S.preventDefault && !S.defaultPrevented) {
          e.cancelEvent(m);
        }
        if (S.stopPropagation) {
          e.stopEvent(m);
        }
      }
      function G(v, m) {
        var w = {
          id: d(m),
          type: g(m),
          isPrimary: y(m),
          currentPos: x(m),
          currentTime: e.now(),
        };
        var S = {
          originalEvent: m,
          eventType: 'pointerout',
          pointerType: w.type,
          isEmulated: false,
        };
        k(v, S);
        ve(v, S, w);
        if (S.preventDefault && !S.defaultPrevented) {
          e.cancelEvent(m);
        }
        if (S.stopPropagation) {
          e.stopEvent(m);
        }
      }
      function $(v, m) {
        var w = {
          id: d(m),
          type: g(m),
          isPrimary: y(m),
          currentPos: x(m),
          currentTime: e.now(),
        };
        var S =
          e.MouseTracker.havePointerEvents &&
          w.type === 'touch' &&
          e.Browser.vendor !== e.BROWSERS.IE;
        var M = {
          originalEvent: m,
          eventType: 'pointerdown',
          pointerType: w.type,
          isEmulated: false,
        };
        k(v, M);
        oe(v, M, w, m.button);
        if (M.preventDefault && !M.defaultPrevented) {
          e.cancelEvent(m);
        }
        if (M.stopPropagation) {
          e.stopEvent(m);
        }
        if (M.shouldCapture) {
          if (S) {
            J(v, w, true);
          } else {
            c(v, w);
          }
        }
      }
      function ae(v, m) {
        ge(v, m);
      }
      function Se(v, m) {
        var w = v.getActivePointersListByType(g(m));
        if (w.getById(m.pointerId)) {
          ge(v, m);
        }
        e.stopEvent(m);
      }
      function ge(v, m) {
        var w = {
          id: d(m),
          type: g(m),
          isPrimary: y(m),
          currentPos: x(m),
          currentTime: e.now(),
        };
        var S = {
          originalEvent: m,
          eventType: 'pointerup',
          pointerType: w.type,
          isEmulated: false,
        };
        k(v, S);
        Ce(v, S, w, m.button);
        if (S.preventDefault && !S.defaultPrevented) {
          e.cancelEvent(m);
        }
        if (S.stopPropagation) {
          e.stopEvent(m);
        }
        if (S.shouldReleaseCapture) {
          if (m.target === v.element) {
            h(v, w);
          } else {
            J(v, w, false);
          }
        }
      }
      function tt(v, m) {
        it(v, m);
      }
      function nt(v, m) {
        var w = v.getActivePointersListByType(g(m));
        if (w.getById(m.pointerId)) {
          it(v, m);
        }
        e.stopEvent(m);
      }
      function it(v, m) {
        var w = {
          id: d(m),
          type: g(m),
          isPrimary: y(m),
          currentPos: x(m),
          currentTime: e.now(),
        };
        var S = {
          originalEvent: m,
          eventType: 'pointermove',
          pointerType: w.type,
          isEmulated: false,
        };
        k(v, S);
        he(v, S, w);
        if (S.preventDefault && !S.defaultPrevented) {
          e.cancelEvent(m);
        }
        if (S.stopPropagation) {
          e.stopEvent(m);
        }
      }
      function p(v, m) {
        var w = { id: m.pointerId, type: g(m) };
        var S = {
          originalEvent: m,
          eventType: 'pointercancel',
          pointerType: w.type,
          isEmulated: false,
        };
        k(v, S);
        He(v, S, w);
        if (S.stopPropagation) {
          e.stopEvent(m);
        }
      }
      function _(v, m) {
        m.speed = 0;
        m.direction = 0;
        m.contactPos = m.currentPos;
        m.contactTime = m.currentTime;
        m.lastPos = m.currentPos;
        m.lastTime = m.currentTime;
        return v.add(m);
      }
      function R(v, m, w) {
        var S;
        var M = m.getById(w.id);
        if (M) {
          if (M.captured) {
            e.console.warn('stopTrackingPointer() called on captured pointer');
            h(v, M);
          }
          if ((m.type === 'mouse' || m.type === 'pen') && m.contacts > 0) {
            m.removeContact();
          }
          S = m.removeById(w.id);
        } else {
          S = m.getLength();
        }
        return S;
      }
      function I(v, m) {
        switch (m.eventType) {
          case 'pointermove':
            m.isStoppable = true;
            m.isCancelable = true;
            m.preventDefault = false;
            m.preventGesture = !v.hasGestureHandlers;
            m.stopPropagation = false;
            break;
          case 'pointerover':
          case 'pointerout':
          case 'contextmenu':
          case 'keydown':
          case 'keyup':
          case 'keypress':
            m.isStoppable = true;
            m.isCancelable = true;
            m.preventDefault = false;
            m.preventGesture = false;
            m.stopPropagation = false;
            break;
          case 'pointerdown':
            m.isStoppable = true;
            m.isCancelable = true;
            m.preventDefault = false;
            m.preventGesture = !v.hasGestureHandlers;
            m.stopPropagation = false;
            break;
          case 'pointerup':
            m.isStoppable = true;
            m.isCancelable = true;
            m.preventDefault = false;
            m.preventGesture = !v.hasGestureHandlers;
            m.stopPropagation = false;
            break;
          case 'wheel':
            m.isStoppable = true;
            m.isCancelable = true;
            m.preventDefault = false;
            m.preventGesture = !v.hasScrollHandler;
            m.stopPropagation = false;
            break;
          case 'gotpointercapture':
          case 'lostpointercapture':
          case 'pointercancel':
            m.isStoppable = true;
            m.isCancelable = false;
            m.preventDefault = false;
            m.preventGesture = false;
            m.stopPropagation = false;
            break;
          case 'click':
            m.isStoppable = true;
            m.isCancelable = true;
            m.preventDefault = !!v.clickHandler;
            m.preventGesture = false;
            m.stopPropagation = false;
            break;
          case 'dblclick':
            m.isStoppable = true;
            m.isCancelable = true;
            m.preventDefault = !!v.dblClickHandler;
            m.preventGesture = false;
            m.stopPropagation = false;
            break;
          case 'focus':
          case 'blur':
          case 'pointerenter':
          case 'pointerleave':
          default:
            m.isStoppable = false;
            m.isCancelable = false;
            m.preventDefault = false;
            m.preventGesture = false;
            m.stopPropagation = false;
            break;
        }
      }
      function k(v, m) {
        m.eventSource = v;
        m.eventPhase =
          m.originalEvent && typeof m.originalEvent.eventPhase != 'undefined'
            ? m.originalEvent.eventPhase
            : 0;
        m.defaultPrevented = e.eventIsCanceled(m.originalEvent);
        m.shouldCapture = false;
        m.shouldReleaseCapture = false;
        m.userData = v.userData;
        I(v, m);
        if (v.preProcessEventHandler) {
          v.preProcessEventHandler(m);
        }
      }
      function J(v, m, w) {
        var S = v.getActivePointersListByType(m.type);
        var M = S.getById(m.id);
        if (M) {
          if (w && !M.captured) {
            M.captured = true;
            S.captureCount++;
          } else if (!w && M.captured) {
            M.captured = false;
            S.captureCount--;
            if (S.captureCount < 0) {
              S.captureCount = 0;
              e.console.warn(
                'updatePointerCaptured() - pointsList.captureCount went negative'
              );
            }
          }
        } else {
          e.console.warn('updatePointerCaptured() called on untracked pointer');
        }
      }
      function X(v, m, w) {
        var S = v.getActivePointersListByType(w.type);
        var M = S.getById(w.id);
        if (M) {
          M.insideElement = true;
          M.lastPos = M.currentPos;
          M.lastTime = M.currentTime;
          M.currentPos = w.currentPos;
          M.currentTime = w.currentTime;
          w = M;
        } else {
          w.captured = false;
          w.insideElementPressed = false;
          w.insideElement = true;
          _(S, w);
        }
        if (v.enterHandler) {
          v.enterHandler({
            eventSource: v,
            pointerType: w.type,
            position: T(w.currentPos, v.element),
            buttons: S.buttons,
            pointers: v.getActivePointerCount(),
            insideElementPressed: w.insideElementPressed,
            buttonDownAny: S.buttons !== 0,
            isTouchEvent: w.type === 'touch',
            originalEvent: m.originalEvent,
            userData: v.userData,
          });
        }
      }
      function pe(v, m, w) {
        var S = v.getActivePointersListByType(w.type);
        var H;
        var M = S.getById(w.id);
        if (M) {
          if (M.captured) {
            M.insideElement = false;
            M.lastPos = M.currentPos;
            M.lastTime = M.currentTime;
            M.currentPos = w.currentPos;
            M.currentTime = w.currentTime;
          } else {
            R(v, S, M);
          }
          w = M;
        } else {
          w.captured = false;
          w.insideElementPressed = false;
        }
        if (v.leaveHandler || v.exitHandler) {
          H = {
            eventSource: v,
            pointerType: w.type,
            position: w.currentPos && T(w.currentPos, v.element),
            buttons: S.buttons,
            pointers: v.getActivePointerCount(),
            insideElementPressed: w.insideElementPressed,
            buttonDownAny: S.buttons !== 0,
            isTouchEvent: w.type === 'touch',
            originalEvent: m.originalEvent,
            userData: v.userData,
          };
          if (v.leaveHandler) {
            v.leaveHandler(H);
          }
          if (v.exitHandler) {
            v.exitHandler(H);
          }
        }
      }
      function _e(v, m, w) {
        var S = v.getActivePointersListByType(w.type);
        var M = S.getById(w.id);
        if (M) {
          w = M;
        } else {
          w.captured = false;
          w.insideElementPressed = false;
        }
        if (v.overHandler) {
          v.overHandler({
            eventSource: v,
            pointerType: w.type,
            position: T(w.currentPos, v.element),
            buttons: S.buttons,
            pointers: v.getActivePointerCount(),
            insideElementPressed: w.insideElementPressed,
            buttonDownAny: S.buttons !== 0,
            isTouchEvent: w.type === 'touch',
            originalEvent: m.originalEvent,
            userData: v.userData,
          });
        }
      }
      function ve(v, m, w) {
        var S = v.getActivePointersListByType(w.type);
        var M = S.getById(w.id);
        if (M) {
          w = M;
        } else {
          w.captured = false;
          w.insideElementPressed = false;
        }
        if (v.outHandler) {
          v.outHandler({
            eventSource: v,
            pointerType: w.type,
            position: w.currentPos && T(w.currentPos, v.element),
            buttons: S.buttons,
            pointers: v.getActivePointerCount(),
            insideElementPressed: w.insideElementPressed,
            buttonDownAny: S.buttons !== 0,
            isTouchEvent: w.type === 'touch',
            originalEvent: m.originalEvent,
            userData: v.userData,
          });
        }
      }
      function oe(v, m, w, S) {
        var M = n[v.hash];
        var H = v.getActivePointersListByType(w.type);
        if (typeof m.originalEvent.buttons == 'undefined') {
          if (S === 0) {
            H.buttons |= 1;
          } else if (S === 1) {
            H.buttons |= 4;
          } else if (S === 2) {
            H.buttons |= 2;
          } else if (S === 3) {
            H.buttons |= 8;
          } else if (S === 4) {
            H.buttons |= 16;
          } else if (S === 5) {
            H.buttons |= 32;
          }
        } else {
          H.buttons = m.originalEvent.buttons;
        }
        if (S !== 0) {
          m.shouldCapture = false;
          m.shouldReleaseCapture = false;
          if (
            v.nonPrimaryPressHandler &&
            !m.preventGesture &&
            !m.defaultPrevented
          ) {
            m.preventDefault = true;
            v.nonPrimaryPressHandler({
              eventSource: v,
              pointerType: w.type,
              position: T(w.currentPos, v.element),
              button: S,
              buttons: H.buttons,
              isTouchEvent: w.type === 'touch',
              originalEvent: m.originalEvent,
              userData: v.userData,
            });
          }
          return;
        }
        var ee = H.getById(w.id);
        if (ee) {
          ee.insideElementPressed = true;
          ee.insideElement = true;
          ee.originalTarget = m.originalEvent.target;
          ee.contactPos = w.currentPos;
          ee.contactTime = w.currentTime;
          ee.lastPos = ee.currentPos;
          ee.lastTime = ee.currentTime;
          ee.currentPos = w.currentPos;
          ee.currentTime = w.currentTime;
          w = ee;
        } else {
          e.console.warn('pointerdown event on untracked pointer');
          w.captured = false;
          w.insideElementPressed = true;
          w.insideElement = true;
          w.originalTarget = m.originalEvent.target;
          _(H, w);
          return;
        }
        H.addContact();
        if (!m.preventGesture && !m.defaultPrevented) {
          m.shouldCapture = true;
          m.shouldReleaseCapture = false;
          m.preventDefault = true;
          if (v.dragHandler || v.dragEndHandler || v.pinchHandler) {
            e.MouseTracker.gesturePointVelocityTracker.addPoint(v, w);
          }
          if (H.contacts === 1) {
            if (v.pressHandler && !m.preventGesture) {
              v.pressHandler({
                eventSource: v,
                pointerType: w.type,
                position: T(w.contactPos, v.element),
                buttons: H.buttons,
                isTouchEvent: w.type === 'touch',
                originalEvent: m.originalEvent,
                userData: v.userData,
              });
            }
          } else if (H.contacts === 2 && v.pinchHandler && w.type === 'touch') {
            M.pinchGPoints = H.asArray();
            M.lastPinchDist = M.currentPinchDist =
              M.pinchGPoints[0].currentPos.distanceTo(
                M.pinchGPoints[1].currentPos
              );
            M.lastPinchCenter = M.currentPinchCenter = f(
              M.pinchGPoints[0].currentPos,
              M.pinchGPoints[1].currentPos
            );
          }
        } else {
          m.shouldCapture = false;
          m.shouldReleaseCapture = false;
        }
      }
      function Ce(v, m, w, S) {
        var M = n[v.hash];
        var H = v.getActivePointersListByType(w.type);
        var ee;
        var xe;
        var rt = false;
        var ke;
        if (typeof m.originalEvent.buttons == 'undefined') {
          if (S === 0) {
            H.buttons ^= ~1;
          } else if (S === 1) {
            H.buttons ^= ~4;
          } else if (S === 2) {
            H.buttons ^= ~2;
          } else if (S === 3) {
            H.buttons ^= ~8;
          } else if (S === 4) {
            H.buttons ^= ~16;
          } else if (S === 5) {
            H.buttons ^= ~32;
          }
        } else {
          H.buttons = m.originalEvent.buttons;
        }
        m.shouldCapture = false;
        if (S !== 0) {
          m.shouldReleaseCapture = false;
          if (
            v.nonPrimaryReleaseHandler &&
            !m.preventGesture &&
            !m.defaultPrevented
          ) {
            m.preventDefault = true;
            v.nonPrimaryReleaseHandler({
              eventSource: v,
              pointerType: w.type,
              position: T(w.currentPos, v.element),
              button: S,
              buttons: H.buttons,
              isTouchEvent: w.type === 'touch',
              originalEvent: m.originalEvent,
              userData: v.userData,
            });
          }
          return;
        }
        var te = H.getById(w.id);
        if (te) {
          H.removeContact();
          if (te.captured) {
            rt = true;
          }
          te.lastPos = te.currentPos;
          te.lastTime = te.currentTime;
          te.currentPos = w.currentPos;
          te.currentTime = w.currentTime;
          if (!te.insideElement) {
            R(v, H, te);
          }
          ee = te.currentPos;
          xe = te.currentTime;
        } else {
          e.console.warn('updatePointerUp(): pointerup on untracked gPoint');
          w.captured = false;
          w.insideElementPressed = false;
          w.insideElement = true;
          _(H, w);
          te = w;
        }
        if (!m.preventGesture && !m.defaultPrevented) {
          if (rt) {
            m.shouldReleaseCapture = true;
            m.preventDefault = true;
            if (v.dragHandler || v.dragEndHandler || v.pinchHandler) {
              e.MouseTracker.gesturePointVelocityTracker.removePoint(v, te);
            }
            if (H.contacts === 0) {
              if (v.releaseHandler) {
                v.releaseHandler({
                  eventSource: v,
                  pointerType: te.type,
                  position: T(ee, v.element),
                  buttons: H.buttons,
                  insideElementPressed: te.insideElementPressed,
                  insideElementReleased: te.insideElement,
                  isTouchEvent: te.type === 'touch',
                  originalEvent: m.originalEvent,
                  userData: v.userData,
                });
              }
              if (v.dragEndHandler && M.sentDragEvent) {
                v.dragEndHandler({
                  eventSource: v,
                  pointerType: te.type,
                  position: T(te.currentPos, v.element),
                  speed: te.speed,
                  direction: te.direction,
                  shift: m.originalEvent.shiftKey,
                  isTouchEvent: te.type === 'touch',
                  originalEvent: m.originalEvent,
                  userData: v.userData,
                });
              }
              M.sentDragEvent = false;
              if ((v.clickHandler || v.dblClickHandler) && te.insideElement) {
                ke =
                  xe - te.contactTime <= v.clickTimeThreshold &&
                  te.contactPos.distanceTo(ee) <= v.clickDistThreshold;
                if (v.clickHandler) {
                  v.clickHandler({
                    eventSource: v,
                    pointerType: te.type,
                    position: T(te.currentPos, v.element),
                    quick: ke,
                    shift: m.originalEvent.shiftKey,
                    isTouchEvent: te.type === 'touch',
                    originalEvent: m.originalEvent,
                    originalTarget: te.originalTarget,
                    userData: v.userData,
                  });
                }
                if (v.dblClickHandler && ke) {
                  H.clicks++;
                  if (H.clicks === 1) {
                    M.lastClickPos = ee;
                    M.dblClickTimeOut = setTimeout(function () {
                      H.clicks = 0;
                    }, v.dblClickTimeThreshold);
                  } else if (H.clicks === 2) {
                    clearTimeout(M.dblClickTimeOut);
                    H.clicks = 0;
                    if (
                      M.lastClickPos.distanceTo(ee) <= v.dblClickDistThreshold
                    ) {
                      v.dblClickHandler({
                        eventSource: v,
                        pointerType: te.type,
                        position: T(te.currentPos, v.element),
                        shift: m.originalEvent.shiftKey,
                        isTouchEvent: te.type === 'touch',
                        originalEvent: m.originalEvent,
                        userData: v.userData,
                      });
                    }
                    M.lastClickPos = null;
                  }
                }
              }
            } else if (
              H.contacts === 2 &&
              v.pinchHandler &&
              te.type === 'touch'
            ) {
              M.pinchGPoints = H.asArray();
              M.lastPinchDist = M.currentPinchDist =
                M.pinchGPoints[0].currentPos.distanceTo(
                  M.pinchGPoints[1].currentPos
                );
              M.lastPinchCenter = M.currentPinchCenter = f(
                M.pinchGPoints[0].currentPos,
                M.pinchGPoints[1].currentPos
              );
            }
          } else {
            m.shouldReleaseCapture = false;
            if (v.releaseHandler) {
              v.releaseHandler({
                eventSource: v,
                pointerType: te.type,
                position: T(ee, v.element),
                buttons: H.buttons,
                insideElementPressed: te.insideElementPressed,
                insideElementReleased: te.insideElement,
                isTouchEvent: te.type === 'touch',
                originalEvent: m.originalEvent,
                userData: v.userData,
              });
              m.preventDefault = true;
            }
          }
        }
      }
      function he(v, m, w) {
        var S = n[v.hash];
        var M = v.getActivePointersListByType(w.type);
        var ee;
        var xe;
        if (typeof m.originalEvent.buttons != 'undefined') {
          M.buttons = m.originalEvent.buttons;
        }
        var H = M.getById(w.id);
        if (H) {
          H.lastPos = H.currentPos;
          H.lastTime = H.currentTime;
          H.currentPos = w.currentPos;
          H.currentTime = w.currentTime;
        } else {
          return;
        }
        m.shouldCapture = false;
        m.shouldReleaseCapture = false;
        if (v.stopHandler && w.type === 'mouse') {
          clearTimeout(v.stopTimeOut);
          v.stopTimeOut = setTimeout(function () {
            Ft(v, m.originalEvent, w.type);
          }, v.stopDelay);
        }
        if (M.contacts === 0) {
          if (v.moveHandler) {
            v.moveHandler({
              eventSource: v,
              pointerType: w.type,
              position: T(w.currentPos, v.element),
              buttons: M.buttons,
              isTouchEvent: w.type === 'touch',
              originalEvent: m.originalEvent,
              userData: v.userData,
            });
          }
        } else if (M.contacts === 1) {
          if (v.moveHandler) {
            H = M.asArray()[0];
            v.moveHandler({
              eventSource: v,
              pointerType: H.type,
              position: T(H.currentPos, v.element),
              buttons: M.buttons,
              isTouchEvent: H.type === 'touch',
              originalEvent: m.originalEvent,
              userData: v.userData,
            });
          }
          if (v.dragHandler && !m.preventGesture && !m.defaultPrevented) {
            H = M.asArray()[0];
            xe = H.currentPos.minus(H.lastPos);
            v.dragHandler({
              eventSource: v,
              pointerType: H.type,
              position: T(H.currentPos, v.element),
              buttons: M.buttons,
              delta: xe,
              speed: H.speed,
              direction: H.direction,
              shift: m.originalEvent.shiftKey,
              isTouchEvent: H.type === 'touch',
              originalEvent: m.originalEvent,
              userData: v.userData,
            });
            m.preventDefault = true;
            S.sentDragEvent = true;
          }
        } else if (M.contacts === 2) {
          if (v.moveHandler) {
            ee = M.asArray();
            v.moveHandler({
              eventSource: v,
              pointerType: ee[0].type,
              position: T(f(ee[0].currentPos, ee[1].currentPos), v.element),
              buttons: M.buttons,
              isTouchEvent: ee[0].type === 'touch',
              originalEvent: m.originalEvent,
              userData: v.userData,
            });
          }
          if (
            v.pinchHandler &&
            w.type === 'touch' &&
            !m.preventGesture &&
            !m.defaultPrevented
          ) {
            xe = S.pinchGPoints[0].currentPos.distanceTo(
              S.pinchGPoints[1].currentPos
            );
            if (xe !== S.currentPinchDist) {
              S.lastPinchDist = S.currentPinchDist;
              S.currentPinchDist = xe;
              S.lastPinchCenter = S.currentPinchCenter;
              S.currentPinchCenter = f(
                S.pinchGPoints[0].currentPos,
                S.pinchGPoints[1].currentPos
              );
              v.pinchHandler({
                eventSource: v,
                pointerType: 'touch',
                gesturePoints: S.pinchGPoints,
                lastCenter: T(S.lastPinchCenter, v.element),
                center: T(S.currentPinchCenter, v.element),
                lastDistance: S.lastPinchDist,
                distance: S.currentPinchDist,
                shift: m.originalEvent.shiftKey,
                originalEvent: m.originalEvent,
                userData: v.userData,
              });
              m.preventDefault = true;
            }
          }
        }
      }
      function He(v, m, w) {
        var S = v.getActivePointersListByType(w.type);
        var M = S.getById(w.id);
        if (M) {
          R(v, S, M);
        }
      }
      function Ft(v, m, w) {
        if (v.stopHandler) {
          v.stopHandler({
            eventSource: v,
            pointerType: w,
            position: b(m, v.element),
            buttons: v.getActivePointersListByType(w).buttons,
            isTouchEvent: w === 'touch',
            originalEvent: m,
            userData: v.userData,
          });
        }
      }
      var e = t;
      var n = {};
      e.MouseTracker = function (v) {
        var m = arguments;
        if (!e.isPlainObject(v)) {
          v = {
            element: m[0],
            clickTimeThreshold: m[1],
            clickDistThreshold: m[2],
          };
        }
        this.hash = Math.random();
        this.element = e.getElement(v.element);
        this.clickTimeThreshold =
          v.clickTimeThreshold || e.DEFAULT_SETTINGS.clickTimeThreshold;
        this.clickDistThreshold =
          v.clickDistThreshold || e.DEFAULT_SETTINGS.clickDistThreshold;
        this.dblClickTimeThreshold =
          v.dblClickTimeThreshold || e.DEFAULT_SETTINGS.dblClickTimeThreshold;
        this.dblClickDistThreshold =
          v.dblClickDistThreshold || e.DEFAULT_SETTINGS.dblClickDistThreshold;
        this.userData = v.userData || null;
        this.stopDelay = v.stopDelay || 50;
        this.preProcessEventHandler = v.preProcessEventHandler || null;
        this.contextMenuHandler = v.contextMenuHandler || null;
        this.enterHandler = v.enterHandler || null;
        this.leaveHandler = v.leaveHandler || null;
        this.exitHandler = v.exitHandler || null;
        this.overHandler = v.overHandler || null;
        this.outHandler = v.outHandler || null;
        this.pressHandler = v.pressHandler || null;
        this.nonPrimaryPressHandler = v.nonPrimaryPressHandler || null;
        this.releaseHandler = v.releaseHandler || null;
        this.nonPrimaryReleaseHandler = v.nonPrimaryReleaseHandler || null;
        this.moveHandler = v.moveHandler || null;
        this.scrollHandler = v.scrollHandler || null;
        this.clickHandler = v.clickHandler || null;
        this.dblClickHandler = v.dblClickHandler || null;
        this.dragHandler = v.dragHandler || null;
        this.dragEndHandler = v.dragEndHandler || null;
        this.pinchHandler = v.pinchHandler || null;
        this.stopHandler = v.stopHandler || null;
        this.keyDownHandler = v.keyDownHandler || null;
        this.keyUpHandler = v.keyUpHandler || null;
        this.keyHandler = v.keyHandler || null;
        this.focusHandler = v.focusHandler || null;
        this.blurHandler = v.blurHandler || null;
        var w = this;
        n[this.hash] = {
          click: function (S) {
            E(w, S);
          },
          dblclick: function (S) {
            A(w, S);
          },
          keydown: function (S) {
            C(w, S);
          },
          keyup: function (S) {
            O(w, S);
          },
          keypress: function (S) {
            D(w, S);
          },
          focus: function (S) {
            N(w, S);
          },
          blur: function (S) {
            B(w, S);
          },
          contextmenu: function (S) {
            Z(w, S);
          },
          wheel: function (S) {
            Y(w, S);
          },
          mousewheel: function (S) {
            U(w, S);
          },
          DOMMouseScroll: function (S) {
            U(w, S);
          },
          MozMousePixelScroll: function (S) {
            U(w, S);
          },
          losecapture: function (S) {
            Q(w, S);
          },
          mouseenter: function (S) {
            V(w, S);
          },
          mouseleave: function (S) {
            z(w, S);
          },
          mouseover: function (S) {
            j(w, S);
          },
          mouseout: function (S) {
            G(w, S);
          },
          mousedown: function (S) {
            $(w, S);
          },
          mouseup: function (S) {
            ae(w, S);
          },
          mousemove: function (S) {
            tt(w, S);
          },
          touchstart: function (S) {
            le(w, S);
          },
          touchend: function (S) {
            re(w, S);
          },
          touchmove: function (S) {
            se(w, S);
          },
          touchcancel: function (S) {
            fe(w, S);
          },
          gesturestart: function (S) {
            me(w, S);
          },
          gesturechange: function (S) {
            q(w, S);
          },
          gotpointercapture: function (S) {
            Le(w, S);
          },
          lostpointercapture: function (S) {
            F(w, S);
          },
          pointerenter: function (S) {
            V(w, S);
          },
          pointerleave: function (S) {
            z(w, S);
          },
          pointerover: function (S) {
            j(w, S);
          },
          pointerout: function (S) {
            G(w, S);
          },
          pointerdown: function (S) {
            $(w, S);
          },
          pointerup: function (S) {
            ae(w, S);
          },
          pointermove: function (S) {
            tt(w, S);
          },
          pointercancel: function (S) {
            p(w, S);
          },
          pointerupcaptured: function (S) {
            Se(w, S);
          },
          pointermovecaptured: function (S) {
            nt(w, S);
          },
          tracking: false,
          activePointersLists: [],
          lastClickPos: null,
          dblClickTimeOut: null,
          pinchGPoints: [],
          lastPinchDist: 0,
          currentPinchDist: 0,
          lastPinchCenter: null,
          currentPinchCenter: null,
          sentDragEvent: false,
        };
        this.hasGestureHandlers =
          !!this.pressHandler ||
          !!this.nonPrimaryPressHandler ||
          !!this.releaseHandler ||
          !!this.nonPrimaryReleaseHandler ||
          !!this.clickHandler ||
          !!this.dblClickHandler ||
          !!this.dragHandler ||
          !!this.dragEndHandler ||
          !!this.pinchHandler;
        this.hasScrollHandler = !!this.scrollHandler;
        if (e.MouseTracker.havePointerEvents) {
          e.setElementPointerEvents(this.element, 'auto');
        }
        if (this.exitHandler) {
          e.console.error(
            'MouseTracker.exitHandler is deprecated. Use MouseTracker.leaveHandler instead.'
          );
        }
        if (!v.startDisabled) {
          this.setTracking(true);
        }
      };
      e.MouseTracker.prototype = {
        destroy: function () {
          l(this);
          this.element = null;
          n[this.hash] = null;
          delete n[this.hash];
        },
        isTracking: function () {
          return n[this.hash].tracking;
        },
        setTracking: function (v) {
          if (v) {
            a(this);
          } else {
            l(this);
          }
          return this;
        },
        getActivePointersListByType: function (v) {
          var m = n[this.hash];
          var S = m.activePointersLists.length;
          for (var w = 0; w < S; w++) {
            if (m.activePointersLists[w].type === v) {
              return m.activePointersLists[w];
            }
          }
          var M = new e.MouseTracker.GesturePointList(v);
          m.activePointersLists.push(M);
          return M;
        },
        getActivePointerCount: function () {
          var v = n[this.hash];
          var w = v.activePointersLists.length;
          var S = 0;
          for (var m = 0; m < w; m++) {
            S += v.activePointersLists[m].getLength();
          }
          return S;
        },
        preProcessEventHandler: function () {},
        contextMenuHandler: function () {},
        enterHandler: function () {},
        leaveHandler: function () {},
        exitHandler: function () {},
        overHandler: function () {},
        outHandler: function () {},
        pressHandler: function () {},
        nonPrimaryPressHandler: function () {},
        releaseHandler: function () {},
        nonPrimaryReleaseHandler: function () {},
        moveHandler: function () {},
        scrollHandler: function () {},
        clickHandler: function () {},
        dblClickHandler: function () {},
        dragHandler: function () {},
        dragEndHandler: function () {},
        pinchHandler: function () {},
        stopHandler: function () {},
        keyDownHandler: function () {},
        keyUpHandler: function () {},
        keyHandler: function () {},
        focusHandler: function () {},
        blurHandler: function () {},
      };
      var r = (function () {
        try {
          return window.self !== window.top;
        } catch {
          return true;
        }
      })();
      e.MouseTracker.gesturePointVelocityTracker = (function () {
        var v = [];
        var m = 0;
        var w = 0;
        var S = function (xe, te) {
          return xe.hash.toString() + te.type + te.id.toString();
        };
        var M = function () {
          var te = v.length;
          var rt;
          var ke;
          var wn = e.now();
          var Bh;
          var Nh;
          var kh = wn - w;
          w = wn;
          for (var xe = 0; xe < te; xe++) {
            rt = v[xe];
            ke = rt.gPoint;
            ke.direction = Math.atan2(
              ke.currentPos.y - rt.lastPos.y,
              ke.currentPos.x - rt.lastPos.x
            );
            Bh = rt.lastPos.distanceTo(ke.currentPos);
            rt.lastPos = ke.currentPos;
            Nh = (1e3 * Bh) / (kh + 1);
            ke.speed = 0.75 * Nh + 0.25 * ke.speed;
          }
        };
        var H = function (xe, te) {
          var rt = S(xe, te);
          v.push({ guid: rt, gPoint: te, lastPos: te.currentPos });
          if (v.length === 1) {
            w = e.now();
            m = window.setInterval(M, 50);
          }
        };
        var ee = function (xe, te) {
          var rt = S(xe, te);
          var wn = v.length;
          for (var ke = 0; ke < wn; ke++) {
            if (v[ke].guid === rt) {
              v.splice(ke, 1);
              wn--;
              if (wn === 0) {
                window.clearInterval(m);
              }
              break;
            }
          }
        };
        return { addPoint: H, removePoint: ee };
      })();
      e.MouseTracker.captureElement = document;
      e.MouseTracker.wheelEventName =
        (e.Browser.vendor === e.BROWSERS.IE && e.Browser.version > 8) ||
        'onwheel' in document.createElement('div')
          ? 'wheel'
          : document.onmousewheel !== void 0
          ? 'mousewheel'
          : 'DOMMouseScroll';
      e.MouseTracker.subscribeEvents = [
        'click',
        'dblclick',
        'keydown',
        'keyup',
        'keypress',
        'focus',
        'blur',
        'contextmenu',
        e.MouseTracker.wheelEventName,
      ];
      if (e.MouseTracker.wheelEventName === 'DOMMouseScroll') {
        e.MouseTracker.subscribeEvents.push('MozMousePixelScroll');
      }
      if (window.PointerEvent) {
        e.MouseTracker.havePointerEvents = true;
        e.MouseTracker.subscribeEvents.push(
          'pointerenter',
          'pointerleave',
          'pointerover',
          'pointerout',
          'pointerdown',
          'pointerup',
          'pointermove',
          'pointercancel'
        );
        e.MouseTracker.havePointerCapture = (function () {
          var v = document.createElement('div');
          return (
            e.isFunction(v.setPointerCapture) &&
            e.isFunction(v.releasePointerCapture)
          );
        })();
        if (e.MouseTracker.havePointerCapture) {
          e.MouseTracker.subscribeEvents.push(
            'gotpointercapture',
            'lostpointercapture'
          );
        }
      } else {
        e.MouseTracker.havePointerEvents = false;
        e.MouseTracker.subscribeEvents.push(
          'mouseenter',
          'mouseleave',
          'mouseover',
          'mouseout',
          'mousedown',
          'mouseup',
          'mousemove'
        );
        e.MouseTracker.mousePointerId = 'legacy-mouse';
        e.MouseTracker.havePointerCapture = (function () {
          var v = document.createElement('div');
          return e.isFunction(v.setCapture) && e.isFunction(v.releaseCapture);
        })();
        if (e.MouseTracker.havePointerCapture) {
          e.MouseTracker.subscribeEvents.push('losecapture');
        }
        if ('ontouchstart' in window) {
          e.MouseTracker.subscribeEvents.push(
            'touchstart',
            'touchend',
            'touchmove',
            'touchcancel'
          );
        }
        if ('ongesturestart' in window) {
          e.MouseTracker.subscribeEvents.push('gesturestart', 'gesturechange');
        }
      }
      e.MouseTracker.GesturePointList = function (v) {
        this._gPoints = [];
        this.type = v;
        this.buttons = 0;
        this.contacts = 0;
        this.clicks = 0;
        this.captureCount = 0;
      };
      e.MouseTracker.GesturePointList.prototype = {
        getLength: function () {
          return this._gPoints.length;
        },
        asArray: function () {
          return this._gPoints;
        },
        add: function (v) {
          return this._gPoints.push(v);
        },
        removeById: function (v) {
          var w = this._gPoints.length;
          for (var m = 0; m < w; m++) {
            if (this._gPoints[m].id === v) {
              this._gPoints.splice(m, 1);
              break;
            }
          }
          return this._gPoints.length;
        },
        getByIndex: function (v) {
          if (v < this._gPoints.length) {
            return this._gPoints[v];
          } else {
            return null;
          }
        },
        getById: function (v) {
          var w = this._gPoints.length;
          for (var m = 0; m < w; m++) {
            if (this._gPoints[m].id === v) {
              return this._gPoints[m];
            }
          }
          return null;
        },
        getPrimary: function (v) {
          var w = this._gPoints.length;
          for (var m = 0; m < w; m++) {
            if (this._gPoints[m].isPrimary) {
              return this._gPoints[m];
            }
          }
          return null;
        },
        addContact: function () {
          ++this.contacts;
          if (
            this.contacts > 1 &&
            (this.type === 'mouse' || this.type === 'pen')
          ) {
            e.console.warn(
              'GesturePointList.addContact() Implausible contacts value'
            );
            this.contacts = 1;
          }
        },
        removeContact: function () {
          --this.contacts;
          if (this.contacts < 0) {
            e.console.warn(
              'GesturePointList.removeContact() Implausible contacts value'
            );
            this.contacts = 0;
          }
        },
      };
    })();
    (function () {
      var e = t;
      e.ControlAnchor = {
        NONE: 0,
        TOP_LEFT: 1,
        TOP_RIGHT: 2,
        BOTTOM_RIGHT: 3,
        BOTTOM_LEFT: 4,
        ABSOLUTE: 5,
      };
      e.Control = function (n, r, o) {
        var s = n.parentNode;
        if (typeof r == 'number') {
          e.console.error(
            'Passing an anchor directly into the OpenSeadragon.Control constructor is deprecated; please use an options object instead.  Support for this deprecated variant is scheduled for removal in December 2013'
          );
          r = { anchor: r };
        }
        r.attachToViewer =
          typeof r.attachToViewer == 'undefined' ? true : r.attachToViewer;
        this.autoFade = typeof r.autoFade == 'undefined' ? true : r.autoFade;
        this.element = n;
        this.anchor = r.anchor;
        this.container = o;
        if (this.anchor === e.ControlAnchor.ABSOLUTE) {
          this.wrapper = e.makeNeutralElement('div');
          this.wrapper.style.position = 'absolute';
          this.wrapper.style.top =
            typeof r.top == 'number' ? r.top + 'px' : r.top;
          this.wrapper.style.left =
            typeof r.left == 'number' ? r.left + 'px' : r.left;
          this.wrapper.style.height =
            typeof r.height == 'number' ? r.height + 'px' : r.height;
          this.wrapper.style.width =
            typeof r.width == 'number' ? r.width + 'px' : r.width;
          this.wrapper.style.margin = '0px';
          this.wrapper.style.padding = '0px';
          this.element.style.position = 'relative';
          this.element.style.top = '0px';
          this.element.style.left = '0px';
          this.element.style.height = '100%';
          this.element.style.width = '100%';
        } else {
          this.wrapper = e.makeNeutralElement('div');
          this.wrapper.style.display = 'inline-block';
          if (this.anchor === e.ControlAnchor.NONE) {
            this.wrapper.style.width = this.wrapper.style.height = '100%';
          }
        }
        this.wrapper.appendChild(this.element);
        if (r.attachToViewer) {
          if (
            this.anchor === e.ControlAnchor.TOP_RIGHT ||
            this.anchor === e.ControlAnchor.BOTTOM_RIGHT
          ) {
            this.container.insertBefore(
              this.wrapper,
              this.container.firstChild
            );
          } else {
            this.container.appendChild(this.wrapper);
          }
        } else {
          s.appendChild(this.wrapper);
        }
      };
      e.Control.prototype = {
        destroy: function () {
          this.wrapper.removeChild(this.element);
          if (this.anchor !== e.ControlAnchor.NONE) {
            this.container.removeChild(this.wrapper);
          }
        },
        isVisible: function () {
          return this.wrapper.style.display !== 'none';
        },
        setVisible: function (n) {
          this.wrapper.style.display = n
            ? this.anchor === e.ControlAnchor.ABSOLUTE
              ? 'block'
              : 'inline-block'
            : 'none';
        },
        setOpacity: function (n) {
          if (this.element[e.SIGNAL] && e.Browser.vendor === e.BROWSERS.IE) {
            e.setElementOpacity(this.element, n, true);
          } else {
            e.setElementOpacity(this.wrapper, n, true);
          }
        },
      };
    })();
    (function () {
      function n(r, o) {
        var s = r.controls;
        for (var a = s.length - 1; a >= 0; a--) {
          if (s[a].element === o) {
            return a;
          }
        }
        return -1;
      }
      var e = t;
      e.ControlDock = function (r) {
        var o = ['topleft', 'topright', 'bottomright', 'bottomleft'];
        var s;
        e.extend(
          true,
          this,
          {
            id:
              'controldock-' + e.now() + '-' + Math.floor(Math.random() * 1e6),
            container: e.makeNeutralElement('div'),
            controls: [],
          },
          r
        );
        this.container.onsubmit = function () {
          return false;
        };
        if (this.element) {
          this.element = e.getElement(this.element);
          this.element.appendChild(this.container);
          this.element.style.position = 'relative';
          this.container.style.width = '100%';
          this.container.style.height = '100%';
        }
        for (var a = 0; a < o.length; a++) {
          s = o[a];
          this.controls[s] = e.makeNeutralElement('div');
          this.controls[s].style.position = 'absolute';
          if (s.match('left')) {
            this.controls[s].style.left = '0px';
          }
          if (s.match('right')) {
            this.controls[s].style.right = '0px';
          }
          if (s.match('top')) {
            this.controls[s].style.top = '0px';
          }
          if (s.match('bottom')) {
            this.controls[s].style.bottom = '0px';
          }
        }
        this.container.appendChild(this.controls.topleft);
        this.container.appendChild(this.controls.topright);
        this.container.appendChild(this.controls.bottomright);
        this.container.appendChild(this.controls.bottomleft);
      };
      e.ControlDock.prototype = {
        addControl: function (r, o) {
          r = e.getElement(r);
          var s = null;
          if (!(n(this, r) >= 0)) {
            switch (o.anchor) {
              case e.ControlAnchor.TOP_RIGHT:
                s = this.controls.topright;
                r.style.position = 'relative';
                r.style.paddingRight = '0px';
                r.style.paddingTop = '0px';
                break;
              case e.ControlAnchor.BOTTOM_RIGHT:
                s = this.controls.bottomright;
                r.style.position = 'relative';
                r.style.paddingRight = '0px';
                r.style.paddingBottom = '0px';
                break;
              case e.ControlAnchor.BOTTOM_LEFT:
                s = this.controls.bottomleft;
                r.style.position = 'relative';
                r.style.paddingLeft = '0px';
                r.style.paddingBottom = '0px';
                break;
              case e.ControlAnchor.TOP_LEFT:
                s = this.controls.topleft;
                r.style.position = 'relative';
                r.style.paddingLeft = '0px';
                r.style.paddingTop = '0px';
                break;
              case e.ControlAnchor.ABSOLUTE:
                s = this.container;
                r.style.margin = '0px';
                r.style.padding = '0px';
                break;
              default:
              case e.ControlAnchor.NONE:
                s = this.container;
                r.style.margin = '0px';
                r.style.padding = '0px';
                break;
            }
            this.controls.push(new e.Control(r, o, s));
            r.style.display = 'inline-block';
          }
        },
        removeControl: function (r) {
          r = e.getElement(r);
          var o = n(this, r);
          if (o >= 0) {
            this.controls[o].destroy();
            this.controls.splice(o, 1);
          }
          return this;
        },
        clearControls: function () {
          while (this.controls.length > 0) {
            this.controls.pop().destroy();
          }
          return this;
        },
        areControlsEnabled: function () {
          for (var r = this.controls.length - 1; r >= 0; r--) {
            if (this.controls[r].isVisible()) {
              return true;
            }
          }
          return false;
        },
        setControlsEnabled: function (r) {
          for (var o = this.controls.length - 1; o >= 0; o--) {
            this.controls[o].setVisible(r);
          }
          return this;
        },
      };
    })();
    (function () {
      var e = t;
      e.Placement = e.freezeObject({
        CENTER: 0,
        TOP_LEFT: 1,
        TOP: 2,
        TOP_RIGHT: 3,
        RIGHT: 4,
        BOTTOM_RIGHT: 5,
        BOTTOM: 6,
        BOTTOM_LEFT: 7,
        LEFT: 8,
        properties: {
          0: {
            isLeft: false,
            isHorizontallyCentered: true,
            isRight: false,
            isTop: false,
            isVerticallyCentered: true,
            isBottom: false,
          },
          1: {
            isLeft: true,
            isHorizontallyCentered: false,
            isRight: false,
            isTop: true,
            isVerticallyCentered: false,
            isBottom: false,
          },
          2: {
            isLeft: false,
            isHorizontallyCentered: true,
            isRight: false,
            isTop: true,
            isVerticallyCentered: false,
            isBottom: false,
          },
          3: {
            isLeft: false,
            isHorizontallyCentered: false,
            isRight: true,
            isTop: true,
            isVerticallyCentered: false,
            isBottom: false,
          },
          4: {
            isLeft: false,
            isHorizontallyCentered: false,
            isRight: true,
            isTop: false,
            isVerticallyCentered: true,
            isBottom: false,
          },
          5: {
            isLeft: false,
            isHorizontallyCentered: false,
            isRight: true,
            isTop: false,
            isVerticallyCentered: false,
            isBottom: true,
          },
          6: {
            isLeft: false,
            isHorizontallyCentered: true,
            isRight: false,
            isTop: false,
            isVerticallyCentered: false,
            isBottom: true,
          },
          7: {
            isLeft: true,
            isHorizontallyCentered: false,
            isRight: false,
            isTop: false,
            isVerticallyCentered: false,
            isBottom: true,
          },
          8: {
            isLeft: true,
            isHorizontallyCentered: false,
            isRight: false,
            isTop: false,
            isVerticallyCentered: true,
            isBottom: false,
          },
        },
      });
    })();
    (function () {
      function o(p) {
        p = e.getElement(p);
        return new e.Point(
          p.clientWidth === 0 ? 1 : p.clientWidth,
          p.clientHeight === 0 ? 1 : p.clientHeight
        );
      }
      function s(p, _, R, I, k) {
        function pe(_e, ve) {
          if (_e.ready) {
            I(_e);
          } else {
            _e.addHandler('ready', function () {
              I(_e);
            });
            _e.addHandler('open-failed', function (oe) {
              k({ message: oe.message, source: ve });
            });
          }
        }
        var J = p;
        if (e.type(_) === 'string') {
          if (_.match(/^\s*<.*>\s*$/)) {
            _ = e.parseXml(_);
          } else if (_.match(/^\s*[{[].*[}\]]\s*$/)) {
            try {
              var X = e.parseJSON(_);
              _ = X;
            } catch {}
          }
        }
        setTimeout(function () {
          if (e.type(_) === 'string') {
            _ = new e.TileSource({
              url: _,
              crossOriginPolicy:
                R.crossOriginPolicy !== void 0
                  ? R.crossOriginPolicy
                  : p.crossOriginPolicy,
              ajaxWithCredentials: p.ajaxWithCredentials,
              ajaxHeaders: R.ajaxHeaders ? R.ajaxHeaders : p.ajaxHeaders,
              useCanvas: p.useCanvas,
              success: function (Ce) {
                I(Ce.tileSource);
              },
            });
            _.addHandler('open-failed', function (Ce) {
              k(Ce);
            });
          } else if (e.isPlainObject(_) || _.nodeType) {
            if (
              _.crossOriginPolicy === void 0 &&
              (R.crossOriginPolicy !== void 0 || p.crossOriginPolicy !== void 0)
            ) {
              _.crossOriginPolicy =
                R.crossOriginPolicy !== void 0
                  ? R.crossOriginPolicy
                  : p.crossOriginPolicy;
            }
            if (_.ajaxWithCredentials === void 0) {
              _.ajaxWithCredentials = p.ajaxWithCredentials;
            }
            if (_.useCanvas === void 0) {
              _.useCanvas = p.useCanvas;
            }
            if (e.isFunction(_.getTileUrl)) {
              var _e = new e.TileSource(_);
              _e.getTileUrl = _.getTileUrl;
              I(_e);
            } else {
              var ve = e.TileSource.determineType(J, _);
              if (!ve) {
                k({ message: 'Unable to load TileSource', source: _ });
                return;
              }
              var oe = ve.prototype.configure.apply(J, [_]);
              pe(new ve(oe), _);
            }
          } else {
            pe(_, _);
          }
        });
      }
      function a(p, _) {
        if (_ instanceof e.Overlay) {
          return _;
        }
        var R = null;
        if (_.element) {
          R = e.getElement(_.element);
        } else {
          var I = _.id
            ? _.id
            : 'openseadragon-overlay-' + Math.floor(Math.random() * 1e7);
          R = e.getElement(_.id);
          if (!R) {
            R = document.createElement('a');
            R.href = '#/overlay/' + I;
          }
          R.id = I;
          e.addClass(R, _.className ? _.className : 'openseadragon-overlay');
        }
        var k = _.location;
        var J = _.width;
        var X = _.height;
        if (!k) {
          var pe = _.x;
          var _e = _.y;
          if (_.px !== void 0) {
            var ve = p.viewport.imageToViewportRectangle(
              new e.Rect(_.px, _.py, J || 0, X || 0)
            );
            pe = ve.x;
            _e = ve.y;
            J = J !== void 0 ? ve.width : void 0;
            X = X !== void 0 ? ve.height : void 0;
          }
          k = new e.Point(pe, _e);
        }
        var oe = _.placement;
        if (oe && e.type(oe) === 'string') {
          oe = e.Placement[_.placement.toUpperCase()];
        }
        return new e.Overlay({
          element: R,
          location: k,
          placement: oe,
          onDraw: _.onDraw,
          checkResize: _.checkResize,
          width: J,
          height: X,
          rotationMode: _.rotationMode,
        });
      }
      function l(p, _) {
        for (var R = p.length - 1; R >= 0; R--) {
          if (p[R].element === _) {
            return R;
          }
        }
        return -1;
      }
      function u(p, _) {
        return e.requestAnimationFrame(function () {
          _(p);
        });
      }
      function c(p) {
        e.requestAnimationFrame(function () {
          d(p);
        });
      }
      function h(p) {
        if (p.autoHideControls) {
          p.controlsShouldFade = true;
          p.controlsFadeBeginTime = e.now() + p.controlsFadeDelay;
          window.setTimeout(function () {
            c(p);
          }, p.controlsFadeDelay);
        }
      }
      function d(p) {
        var _;
        var R;
        var I;
        var k;
        if (p.controlsShouldFade) {
          _ = e.now();
          R = _ - p.controlsFadeBeginTime;
          I = 1 - R / p.controlsFadeLength;
          I = Math.min(1, I);
          I = Math.max(0, I);
          for (k = p.controls.length - 1; k >= 0; k--) {
            if (p.controls[k].autoFade) {
              p.controls[k].setOpacity(I);
            }
          }
          if (I > 0) {
            c(p);
          }
        }
      }
      function g(p) {
        p.controlsShouldFade = false;
        for (var _ = p.controls.length - 1; _ >= 0; _--) {
          p.controls[_].setOpacity(1);
        }
      }
      function y() {
        g(this);
      }
      function x() {
        h(this);
      }
      function b(p) {
        var _ = {
          tracker: p.eventSource,
          position: p.position,
          originalEvent: p.originalEvent,
          preventDefault: p.preventDefault,
        };
        this.raiseEvent('canvas-contextmenu', _);
        p.preventDefault = _.preventDefault;
      }
      function T(p) {
        var _ = {
          originalEvent: p.originalEvent,
          preventDefaultAction: false,
          preventVerticalPan: p.preventVerticalPan,
          preventHorizontalPan: p.preventHorizontalPan,
        };
        this.raiseEvent('canvas-key', _);
        if (!_.preventDefaultAction && !p.ctrl && !p.alt && !p.meta) {
          switch (p.keyCode) {
            case 38:
              if (!_.preventVerticalPan) {
                if (p.shift) {
                  this.viewport.zoomBy(1.1);
                } else {
                  this.viewport.panBy(
                    this.viewport.deltaPointsFromPixels(
                      new e.Point(0, -this.pixelsPerArrowPress)
                    )
                  );
                }
                this.viewport.applyConstraints();
              }
              p.preventDefault = true;
              break;
            case 40:
              if (!_.preventVerticalPan) {
                if (p.shift) {
                  this.viewport.zoomBy(0.9);
                } else {
                  this.viewport.panBy(
                    this.viewport.deltaPointsFromPixels(
                      new e.Point(0, this.pixelsPerArrowPress)
                    )
                  );
                }
                this.viewport.applyConstraints();
              }
              p.preventDefault = true;
              break;
            case 37:
              if (!_.preventHorizontalPan) {
                this.viewport.panBy(
                  this.viewport.deltaPointsFromPixels(
                    new e.Point(-this.pixelsPerArrowPress, 0)
                  )
                );
                this.viewport.applyConstraints();
              }
              p.preventDefault = true;
              break;
            case 39:
              if (!_.preventHorizontalPan) {
                this.viewport.panBy(
                  this.viewport.deltaPointsFromPixels(
                    new e.Point(this.pixelsPerArrowPress, 0)
                  )
                );
                this.viewport.applyConstraints();
              }
              p.preventDefault = true;
              break;
            default:
              p.preventDefault = false;
              break;
          }
        } else {
          p.preventDefault = false;
        }
      }
      function f(p) {
        var _ = {
          originalEvent: p.originalEvent,
          preventDefaultAction: false,
          preventVerticalPan: p.preventVerticalPan,
          preventHorizontalPan: p.preventHorizontalPan,
        };
        this.raiseEvent('canvas-key', _);
        if (!_.preventDefaultAction && !p.ctrl && !p.alt && !p.meta) {
          switch (p.keyCode) {
            case 43:
            case 61:
              this.viewport.zoomBy(1.1);
              this.viewport.applyConstraints();
              p.preventDefault = true;
              break;
            case 45:
              this.viewport.zoomBy(0.9);
              this.viewport.applyConstraints();
              p.preventDefault = true;
              break;
            case 48:
              this.viewport.goHome();
              this.viewport.applyConstraints();
              p.preventDefault = true;
              break;
            case 119:
            case 87:
              if (!_.preventVerticalPan) {
                if (p.shift) {
                  this.viewport.zoomBy(1.1);
                } else {
                  this.viewport.panBy(
                    this.viewport.deltaPointsFromPixels(new e.Point(0, -40))
                  );
                }
                this.viewport.applyConstraints();
              }
              p.preventDefault = true;
              break;
            case 115:
            case 83:
              if (!_.preventVerticalPan) {
                if (p.shift) {
                  this.viewport.zoomBy(0.9);
                } else {
                  this.viewport.panBy(
                    this.viewport.deltaPointsFromPixels(new e.Point(0, 40))
                  );
                }
                this.viewport.applyConstraints();
              }
              p.preventDefault = true;
              break;
            case 97:
              if (!_.preventHorizontalPan) {
                this.viewport.panBy(
                  this.viewport.deltaPointsFromPixels(new e.Point(-40, 0))
                );
                this.viewport.applyConstraints();
              }
              p.preventDefault = true;
              break;
            case 100:
              if (!_.preventHorizontalPan) {
                this.viewport.panBy(
                  this.viewport.deltaPointsFromPixels(new e.Point(40, 0))
                );
                this.viewport.applyConstraints();
              }
              p.preventDefault = true;
              break;
            case 114:
              if (this.viewport.flipped) {
                this.viewport.setRotation(
                  e.positiveModulo(
                    this.viewport.degrees - this.rotationIncrement,
                    360
                  )
                );
              } else {
                this.viewport.setRotation(
                  e.positiveModulo(
                    this.viewport.degrees + this.rotationIncrement,
                    360
                  )
                );
              }
              this.viewport.applyConstraints();
              p.preventDefault = true;
              break;
            case 82:
              if (this.viewport.flipped) {
                this.viewport.setRotation(
                  e.positiveModulo(
                    this.viewport.degrees + this.rotationIncrement,
                    360
                  )
                );
              } else {
                this.viewport.setRotation(
                  e.positiveModulo(
                    this.viewport.degrees - this.rotationIncrement,
                    360
                  )
                );
              }
              this.viewport.applyConstraints();
              p.preventDefault = true;
              break;
            case 102:
              this.viewport.toggleFlip();
              p.preventDefault = true;
              break;
            case 106:
              this.goToPreviousPage();
              break;
            case 107:
              this.goToNextPage();
              break;
            default:
              p.preventDefault = false;
              break;
          }
        } else {
          p.preventDefault = false;
        }
      }
      function E(p) {
        var _;
        var R = document.activeElement === this.canvas;
        if (!R) {
          this.canvas.focus();
        }
        if (this.viewport.flipped) {
          p.position.x = this.viewport.getContainerSize().x - p.position.x;
        }
        var I = {
          tracker: p.eventSource,
          position: p.position,
          quick: p.quick,
          shift: p.shift,
          originalEvent: p.originalEvent,
          originalTarget: p.originalTarget,
          preventDefaultAction: false,
        };
        this.raiseEvent('canvas-click', I);
        if (!I.preventDefaultAction && this.viewport && p.quick) {
          _ = this.gestureSettingsByDeviceType(p.pointerType);
          if (_.clickToZoom) {
            this.viewport.zoomBy(
              p.shift ? 1 / this.zoomPerClick : this.zoomPerClick,
              _.zoomToRefPoint
                ? this.viewport.pointFromPixel(p.position, true)
                : null
            );
            this.viewport.applyConstraints();
          }
        }
      }
      function A(p) {
        var _;
        var R = {
          tracker: p.eventSource,
          position: p.position,
          shift: p.shift,
          originalEvent: p.originalEvent,
          preventDefaultAction: false,
        };
        this.raiseEvent('canvas-double-click', R);
        if (!R.preventDefaultAction && this.viewport) {
          _ = this.gestureSettingsByDeviceType(p.pointerType);
          if (_.dblClickToZoom) {
            this.viewport.zoomBy(
              p.shift ? 1 / this.zoomPerClick : this.zoomPerClick,
              _.zoomToRefPoint
                ? this.viewport.pointFromPixel(p.position, true)
                : null
            );
            this.viewport.applyConstraints();
          }
        }
      }
      function C(p) {
        var R = {
          tracker: p.eventSource,
          pointerType: p.pointerType,
          position: p.position,
          delta: p.delta,
          speed: p.speed,
          direction: p.direction,
          shift: p.shift,
          originalEvent: p.originalEvent,
          preventDefaultAction: false,
        };
        this.raiseEvent('canvas-drag', R);
        var _ = this.gestureSettingsByDeviceType(p.pointerType);
        if (_.dragToPan && !R.preventDefaultAction && this.viewport) {
          if (!this.panHorizontal) {
            p.delta.x = 0;
          }
          if (!this.panVertical) {
            p.delta.y = 0;
          }
          if (this.viewport.flipped) {
            p.delta.x = -p.delta.x;
          }
          if (this.constrainDuringPan) {
            var I = this.viewport.deltaPointsFromPixels(p.delta.negate());
            this.viewport.centerSpringX.target.value += I.x;
            this.viewport.centerSpringY.target.value += I.y;
            var k = this.viewport.getBounds();
            var J = this.viewport.getConstrainedBounds();
            this.viewport.centerSpringX.target.value -= I.x;
            this.viewport.centerSpringY.target.value -= I.y;
            if (k.x !== J.x) {
              p.delta.x = 0;
            }
            if (k.y !== J.y) {
              p.delta.y = 0;
            }
          }
          this.viewport.panBy(
            this.viewport.deltaPointsFromPixels(p.delta.negate()),
            _.flickEnabled && !this.constrainDuringPan
          );
        }
      }
      function O(p) {
        var _ = {
          tracker: p.eventSource,
          pointerType: p.pointerType,
          position: p.position,
          speed: p.speed,
          direction: p.direction,
          shift: p.shift,
          originalEvent: p.originalEvent,
          preventDefaultAction: false,
        };
        this.raiseEvent('canvas-drag-end', _);
        if (!_.preventDefaultAction && this.viewport) {
          var R = this.gestureSettingsByDeviceType(p.pointerType);
          if (R.flickEnabled && p.speed >= R.flickMinSpeed) {
            var I = 0;
            if (this.panHorizontal) {
              I = R.flickMomentum * p.speed * Math.cos(p.direction);
            }
            var k = 0;
            if (this.panVertical) {
              k = R.flickMomentum * p.speed * Math.sin(p.direction);
            }
            var J = this.viewport.pixelFromPoint(this.viewport.getCenter(true));
            var X = this.viewport.pointFromPixel(new e.Point(J.x - I, J.y - k));
            this.viewport.panTo(X, false);
          }
          this.viewport.applyConstraints();
        }
      }
      function D(p) {
        this.raiseEvent('canvas-enter', {
          tracker: p.eventSource,
          pointerType: p.pointerType,
          position: p.position,
          buttons: p.buttons,
          pointers: p.pointers,
          insideElementPressed: p.insideElementPressed,
          buttonDownAny: p.buttonDownAny,
          originalEvent: p.originalEvent,
        });
      }
      function N(p) {
        this.raiseEvent('canvas-exit', {
          tracker: p.eventSource,
          pointerType: p.pointerType,
          position: p.position,
          buttons: p.buttons,
          pointers: p.pointers,
          insideElementPressed: p.insideElementPressed,
          buttonDownAny: p.buttonDownAny,
          originalEvent: p.originalEvent,
        });
      }
      function B(p) {
        this.raiseEvent('canvas-press', {
          tracker: p.eventSource,
          pointerType: p.pointerType,
          position: p.position,
          insideElementPressed: p.insideElementPressed,
          insideElementReleased: p.insideElementReleased,
          originalEvent: p.originalEvent,
        });
      }
      function Z(p) {
        this.raiseEvent('canvas-release', {
          tracker: p.eventSource,
          pointerType: p.pointerType,
          position: p.position,
          insideElementPressed: p.insideElementPressed,
          insideElementReleased: p.insideElementReleased,
          originalEvent: p.originalEvent,
        });
      }
      function Y(p) {
        this.raiseEvent('canvas-nonprimary-press', {
          tracker: p.eventSource,
          position: p.position,
          pointerType: p.pointerType,
          button: p.button,
          buttons: p.buttons,
          originalEvent: p.originalEvent,
        });
      }
      function U(p) {
        this.raiseEvent('canvas-nonprimary-release', {
          tracker: p.eventSource,
          position: p.position,
          pointerType: p.pointerType,
          button: p.button,
          buttons: p.buttons,
          originalEvent: p.originalEvent,
        });
      }
      function K(p) {
        var _;
        var R;
        var I;
        var k;
        var J = {
          tracker: p.eventSource,
          pointerType: p.pointerType,
          gesturePoints: p.gesturePoints,
          lastCenter: p.lastCenter,
          center: p.center,
          lastDistance: p.lastDistance,
          distance: p.distance,
          shift: p.shift,
          originalEvent: p.originalEvent,
          preventDefaultPanAction: false,
          preventDefaultZoomAction: false,
          preventDefaultRotateAction: false,
        };
        this.raiseEvent('canvas-pinch', J);
        if (this.viewport) {
          _ = this.gestureSettingsByDeviceType(p.pointerType);
          if (
            _.pinchToZoom &&
            (!J.preventDefaultPanAction || !J.preventDefaultZoomAction)
          ) {
            R = this.viewport.pointFromPixel(p.center, true);
            if (!J.preventDefaultZoomAction) {
              this.viewport.zoomBy(p.distance / p.lastDistance, R, true);
            }
            if (_.zoomToRefPoint && !J.preventDefaultPanAction) {
              I = this.viewport.pointFromPixel(p.lastCenter, true);
              k = I.minus(R);
              if (!this.panHorizontal) {
                k.x = 0;
              }
              if (!this.panVertical) {
                k.y = 0;
              }
              this.viewport.panBy(k, true);
            }
            this.viewport.applyConstraints();
          }
          if (_.pinchRotate && !J.preventDefaultRotateAction) {
            var X = Math.atan2(
              p.gesturePoints[0].currentPos.y - p.gesturePoints[1].currentPos.y,
              p.gesturePoints[0].currentPos.x - p.gesturePoints[1].currentPos.x
            );
            var pe = Math.atan2(
              p.gesturePoints[0].lastPos.y - p.gesturePoints[1].lastPos.y,
              p.gesturePoints[0].lastPos.x - p.gesturePoints[1].lastPos.x
            );
            this.viewport.setRotation(
              this.viewport.getRotation() + (X - pe) * (180 / Math.PI)
            );
          }
        }
      }
      function Q(p) {
        var _;
        var R;
        var I;
        var k = e.now();
        var J = k - this._lastScrollTime;
        if (J > this.minScrollDeltaTime) {
          this._lastScrollTime = k;
          _ = {
            tracker: p.eventSource,
            position: p.position,
            scroll: p.scroll,
            shift: p.shift,
            originalEvent: p.originalEvent,
            preventDefaultAction: false,
            preventDefault: true,
          };
          this.raiseEvent('canvas-scroll', _);
          if (!_.preventDefaultAction && this.viewport) {
            if (this.viewport.flipped) {
              p.position.x = this.viewport.getContainerSize().x - p.position.x;
            }
            R = this.gestureSettingsByDeviceType(p.pointerType);
            if (R.scrollToZoom) {
              I = Math.pow(this.zoomPerScroll, p.scroll);
              this.viewport.zoomBy(
                I,
                R.zoomToRefPoint
                  ? this.viewport.pointFromPixel(p.position, true)
                  : null
              );
              this.viewport.applyConstraints();
            }
          }
          p.preventDefault = _.preventDefault;
        } else {
          p.preventDefault = true;
        }
      }
      function le(p) {
        n[this.hash].mouseInside = true;
        g(this);
        this.raiseEvent('container-enter', {
          tracker: p.eventSource,
          pointerType: p.pointerType,
          position: p.position,
          buttons: p.buttons,
          pointers: p.pointers,
          insideElementPressed: p.insideElementPressed,
          buttonDownAny: p.buttonDownAny,
          originalEvent: p.originalEvent,
        });
      }
      function re(p) {
        if (p.pointers < 1) {
          n[this.hash].mouseInside = false;
          if (!n[this.hash].animating) {
            h(this);
          }
        }
        this.raiseEvent('container-exit', {
          tracker: p.eventSource,
          pointerType: p.pointerType,
          position: p.position,
          buttons: p.buttons,
          pointers: p.pointers,
          insideElementPressed: p.insideElementPressed,
          buttonDownAny: p.buttonDownAny,
          originalEvent: p.originalEvent,
        });
      }
      function se(p) {
        fe(p);
        if (p.isOpen()) {
          p._updateRequestId = u(p, se);
        } else {
          p._updateRequestId = false;
        }
      }
      function fe(p) {
        if (!p._opening && !!n[p.hash]) {
          if (p.autoResize) {
            var _ = o(p.container);
            var R = n[p.hash].prevContainerSize;
            if (!_.equals(R)) {
              var I = p.viewport;
              if (p.preserveImageSizeOnResize) {
                var k = R.x / _.x;
                var J = I.getZoom() * k;
                var X = I.getCenter();
                I.resize(_, false);
                I.zoomTo(J, null, true);
                I.panTo(X, true);
              } else {
                var pe = I.getBounds();
                I.resize(_, true);
                I.fitBoundsWithConstraints(pe, true);
              }
              n[p.hash].prevContainerSize = _;
              n[p.hash].forceRedraw = true;
            }
          }
          var _e = p.viewport.update();
          var ve = p.world.update() || _e;
          if (_e) {
            p.raiseEvent('viewport-change');
          }
          if (p.referenceStrip) {
            ve = p.referenceStrip.update(p.viewport) || ve;
          }
          if (!n[p.hash].animating && ve) {
            p.raiseEvent('animation-start');
            g(p);
          }
          if (ve || n[p.hash].forceRedraw || p.world.needsDraw()) {
            me(p);
            p._drawOverlays();
            if (p.navigator) {
              p.navigator.update(p.viewport);
            }
            n[p.hash].forceRedraw = false;
            if (ve) {
              p.raiseEvent('animation');
            }
          }
          if (n[p.hash].animating && !ve) {
            p.raiseEvent('animation-finish');
            if (!n[p.hash].mouseInside) {
              h(p);
            }
          }
          n[p.hash].animating = ve;
        }
      }
      function me(p) {
        p.imageLoader.clear();
        p.drawer.clear();
        p.world.draw();
        p.raiseEvent('update-viewport', {});
      }
      function q(p, _) {
        if (p) {
          return p + _;
        } else {
          return _;
        }
      }
      function Le() {
        n[this.hash].lastZoomTime = e.now();
        n[this.hash].zoomFactor = this.zoomPerSecond;
        n[this.hash].zooming = true;
        z(this);
      }
      function F() {
        n[this.hash].lastZoomTime = e.now();
        n[this.hash].zoomFactor = 1 / this.zoomPerSecond;
        n[this.hash].zooming = true;
        z(this);
      }
      function V() {
        n[this.hash].zooming = false;
      }
      function z(p) {
        e.requestAnimationFrame(e.delegate(p, j));
      }
      function j() {
        var p;
        var _;
        var R;
        if (n[this.hash].zooming && this.viewport) {
          p = e.now();
          _ = p - n[this.hash].lastZoomTime;
          R = Math.pow(n[this.hash].zoomFactor, _ / 1e3);
          this.viewport.zoomBy(R);
          this.viewport.applyConstraints();
          n[this.hash].lastZoomTime = p;
          z(this);
        }
      }
      function G() {
        if (this.viewport) {
          n[this.hash].zooming = false;
          this.viewport.zoomBy(this.zoomPerClick / 1);
          this.viewport.applyConstraints();
        }
      }
      function $() {
        if (this.viewport) {
          n[this.hash].zooming = false;
          this.viewport.zoomBy(1 / this.zoomPerClick);
          this.viewport.applyConstraints();
        }
      }
      function ae() {
        if (this.buttonGroup) {
          this.buttonGroup.emulateEnter();
          this.buttonGroup.emulateLeave();
        }
      }
      function Se() {
        if (this.viewport) {
          this.viewport.goHome();
        }
      }
      function ge() {
        if (this.isFullPage() && !e.isFullScreen()) {
          this.setFullPage(false);
        } else {
          this.setFullScreen(!this.isFullPage());
        }
        if (this.buttonGroup) {
          this.buttonGroup.emulateLeave();
        }
        this.fullPageButton.element.focus();
        if (this.viewport) {
          this.viewport.applyConstraints();
        }
      }
      function tt() {
        if (this.viewport) {
          var p = this.viewport.getRotation();
          if (this.viewport.flipped) {
            p = e.positiveModulo(p + this.rotationIncrement, 360);
          } else {
            p = e.positiveModulo(p - this.rotationIncrement, 360);
          }
          this.viewport.setRotation(p);
        }
      }
      function nt() {
        if (this.viewport) {
          var p = this.viewport.getRotation();
          if (this.viewport.flipped) {
            p = e.positiveModulo(p - this.rotationIncrement, 360);
          } else {
            p = e.positiveModulo(p + this.rotationIncrement, 360);
          }
          this.viewport.setRotation(p);
        }
      }
      function it() {
        this.viewport.toggleFlip();
      }
      var e = t;
      var n = {};
      var r = 1;
      e.Viewer = function (p) {
        var _ = arguments;
        var R = this;
        if (!e.isPlainObject(p)) {
          p = {
            id: _[0],
            xmlPath: _.length > 1 ? _[1] : void 0,
            prefixUrl: _.length > 2 ? _[2] : void 0,
            controls: _.length > 3 ? _[3] : void 0,
            overlays: _.length > 4 ? _[4] : void 0,
          };
        }
        if (p.config) {
          e.extend(true, p, p.config);
          delete p.config;
        }
        e.extend(
          true,
          this,
          {
            id: p.id,
            hash: p.hash || r++,
            initialPage: 0,
            element: null,
            container: null,
            canvas: null,
            overlays: [],
            overlaysContainer: null,
            previousBody: [],
            customControls: [],
            source: null,
            drawer: null,
            world: null,
            viewport: null,
            navigator: null,
            collectionViewport: null,
            collectionDrawer: null,
            navImages: null,
            buttons: null,
            profiler: null,
          },
          e.DEFAULT_SETTINGS,
          p
        );
        if (typeof this.hash == 'undefined') {
          throw new Error(
            'A hash must be defined, either by specifying options.id or options.hash.'
          );
        }
        if (typeof n[this.hash] != 'undefined') {
          e.console.warn('Hash ' + this.hash + ' has already been used.');
        }
        n[this.hash] = {
          fsBoundsDelta: new e.Point(1, 1),
          prevContainerSize: null,
          animating: false,
          forceRedraw: false,
          mouseInside: false,
          group: null,
          zooming: false,
          zoomFactor: null,
          lastZoomTime: null,
          fullPage: false,
          onfullscreenchange: null,
        };
        this._sequenceIndex = 0;
        this._firstOpen = true;
        this._updateRequestId = null;
        this._loadQueue = [];
        this.currentOverlays = [];
        this._updatePixelDensityRatioBind = null;
        this._lastScrollTime = e.now();
        e.EventSource.call(this);
        this.addHandler('open-failed', function (k) {
          var J = e.getString('Errors.OpenFailed', k.eventSource, k.message);
          R._showMessage(J);
        });
        e.ControlDock.call(this, p);
        if (this.xmlPath) {
          this.tileSources = [this.xmlPath];
        }
        this.element = this.element || document.getElementById(this.id);
        this.canvas = e.makeNeutralElement('div');
        this.canvas.className = 'openseadragon-canvas';
        (function (k) {
          k.width = '100%';
          k.height = '100%';
          k.overflow = 'hidden';
          k.position = 'absolute';
          k.top = '0px';
          k.left = '0px';
        })(this.canvas.style);
        e.setElementTouchActionNone(this.canvas);
        if (p.tabIndex !== '') {
          this.canvas.tabIndex = p.tabIndex === void 0 ? 0 : p.tabIndex;
        }
        this.container.className = 'openseadragon-container';
        (function (k) {
          k.width = '100%';
          k.height = '100%';
          k.position = 'relative';
          k.overflow = 'hidden';
          k.left = '0px';
          k.top = '0px';
          k.textAlign = 'left';
        })(this.container.style);
        e.setElementTouchActionNone(this.container);
        this.container.insertBefore(this.canvas, this.container.firstChild);
        this.element.appendChild(this.container);
        this.bodyWidth = document.body.style.width;
        this.bodyHeight = document.body.style.height;
        this.bodyOverflow = document.body.style.overflow;
        this.docOverflow = document.documentElement.style.overflow;
        this.innerTracker = new e.MouseTracker({
          userData: 'Viewer.innerTracker',
          element: this.canvas,
          startDisabled: !this.mouseNavEnabled,
          clickTimeThreshold: this.clickTimeThreshold,
          clickDistThreshold: this.clickDistThreshold,
          dblClickTimeThreshold: this.dblClickTimeThreshold,
          dblClickDistThreshold: this.dblClickDistThreshold,
          contextMenuHandler: e.delegate(this, b),
          keyDownHandler: e.delegate(this, T),
          keyHandler: e.delegate(this, f),
          clickHandler: e.delegate(this, E),
          dblClickHandler: e.delegate(this, A),
          dragHandler: e.delegate(this, C),
          dragEndHandler: e.delegate(this, O),
          enterHandler: e.delegate(this, D),
          leaveHandler: e.delegate(this, N),
          pressHandler: e.delegate(this, B),
          releaseHandler: e.delegate(this, Z),
          nonPrimaryPressHandler: e.delegate(this, Y),
          nonPrimaryReleaseHandler: e.delegate(this, U),
          scrollHandler: e.delegate(this, Q),
          pinchHandler: e.delegate(this, K),
        });
        this.outerTracker = new e.MouseTracker({
          userData: 'Viewer.outerTracker',
          element: this.container,
          startDisabled: !this.mouseNavEnabled,
          clickTimeThreshold: this.clickTimeThreshold,
          clickDistThreshold: this.clickDistThreshold,
          dblClickTimeThreshold: this.dblClickTimeThreshold,
          dblClickDistThreshold: this.dblClickDistThreshold,
          enterHandler: e.delegate(this, le),
          leaveHandler: e.delegate(this, re),
        });
        if (this.toolbar) {
          this.toolbar = new e.ControlDock({ element: this.toolbar });
        }
        this.bindStandardControls();
        n[this.hash].prevContainerSize = o(this.container);
        this.world = new e.World({ viewer: this });
        this.world.addHandler('add-item', function (k) {
          R.source = R.world.getItemAt(0).source;
          n[R.hash].forceRedraw = true;
          if (!R._updateRequestId) {
            R._updateRequestId = u(R, se);
          }
        });
        this.world.addHandler('remove-item', function (k) {
          if (R.world.getItemCount()) {
            R.source = R.world.getItemAt(0).source;
          } else {
            R.source = null;
          }
          n[R.hash].forceRedraw = true;
        });
        this.world.addHandler('metrics-change', function (k) {
          if (R.viewport) {
            R.viewport._setContentBounds(
              R.world.getHomeBounds(),
              R.world.getContentFactor()
            );
          }
        });
        this.world.addHandler('item-index-change', function (k) {
          R.source = R.world.getItemAt(0).source;
        });
        this.viewport = new e.Viewport({
          containerSize: n[this.hash].prevContainerSize,
          springStiffness: this.springStiffness,
          animationTime: this.animationTime,
          minZoomImageRatio: this.minZoomImageRatio,
          maxZoomPixelRatio: this.maxZoomPixelRatio,
          visibilityRatio: this.visibilityRatio,
          wrapHorizontal: this.wrapHorizontal,
          wrapVertical: this.wrapVertical,
          defaultZoomLevel: this.defaultZoomLevel,
          minZoomLevel: this.minZoomLevel,
          maxZoomLevel: this.maxZoomLevel,
          viewer: this,
          degrees: this.degrees,
          flipped: this.flipped,
          navigatorRotate: this.navigatorRotate,
          homeFillsViewer: this.homeFillsViewer,
          margins: this.viewportMargins,
        });
        this.viewport._setContentBounds(
          this.world.getHomeBounds(),
          this.world.getContentFactor()
        );
        this.imageLoader = new e.ImageLoader({
          jobLimit: this.imageLoaderLimit,
          timeout: p.timeout,
        });
        this.tileCache = new e.TileCache({
          maxImageCacheCount: this.maxImageCacheCount,
        });
        this.drawer = new e.Drawer({
          viewer: this,
          viewport: this.viewport,
          element: this.canvas,
          debugGridColor: this.debugGridColor,
        });
        this.overlaysContainer = e.makeNeutralElement('div');
        this.canvas.appendChild(this.overlaysContainer);
        if (!this.drawer.canRotate()) {
          if (this.rotateLeft) {
            I = this.buttonGroup.buttons.indexOf(this.rotateLeft);
            this.buttonGroup.buttons.splice(I, 1);
            this.buttonGroup.element.removeChild(this.rotateLeft.element);
          }
          if (this.rotateRight) {
            I = this.buttonGroup.buttons.indexOf(this.rotateRight);
            this.buttonGroup.buttons.splice(I, 1);
            this.buttonGroup.element.removeChild(this.rotateRight.element);
          }
        }
        this._addUpdatePixelDensityRatioEvent();
        if (this.showNavigator) {
          this.navigator = new e.Navigator({
            id: this.navigatorId,
            position: this.navigatorPosition,
            sizeRatio: this.navigatorSizeRatio,
            maintainSizeRatio: this.navigatorMaintainSizeRatio,
            top: this.navigatorTop,
            left: this.navigatorLeft,
            width: this.navigatorWidth,
            height: this.navigatorHeight,
            autoResize: this.navigatorAutoResize,
            autoFade: this.navigatorAutoFade,
            prefixUrl: this.prefixUrl,
            viewer: this,
            navigatorRotate: this.navigatorRotate,
            background: this.navigatorBackground,
            opacity: this.navigatorOpacity,
            borderColor: this.navigatorBorderColor,
            displayRegionColor: this.navigatorDisplayRegionColor,
            crossOriginPolicy: this.crossOriginPolicy,
          });
        }
        if (this.sequenceMode) {
          this.bindSequenceControls();
        }
        if (this.tileSources) {
          this.open(this.tileSources);
        }
        for (var I = 0; I < this.customControls.length; I++) {
          this.addControl(this.customControls[I].id, {
            anchor: this.customControls[I].anchor,
          });
        }
        e.requestAnimationFrame(function () {
          h(R);
        });
        if (
          this.imageSmoothingEnabled !== void 0 &&
          !this.imageSmoothingEnabled
        ) {
          this.drawer.setImageSmoothingEnabled(this.imageSmoothingEnabled);
        }
        e._viewers.set(this.element, this);
      };
      e.extend(
        e.Viewer.prototype,
        e.EventSource.prototype,
        e.ControlDock.prototype,
        {
          isOpen: function () {
            return !!this.world.getItemCount();
          },
          openDzi: function (p) {
            e.console.error(
              '[Viewer.openDzi] this function is deprecated; use Viewer.open() instead.'
            );
            return this.open(p);
          },
          openTileSource: function (p) {
            e.console.error(
              '[Viewer.openTileSource] this function is deprecated; use Viewer.open() instead.'
            );
            return this.open(p);
          },
          open: function (p, _) {
            var R = this;
            this.close();
            if (!p) {
              return this;
            }
            if (this.sequenceMode && e.isArray(p)) {
              if (this.referenceStrip) {
                this.referenceStrip.destroy();
                this.referenceStrip = null;
              }
              if (typeof _ != 'undefined' && !isNaN(_)) {
                this.initialPage = _;
              }
              this.tileSources = p;
              this._sequenceIndex = Math.max(
                0,
                Math.min(this.tileSources.length - 1, this.initialPage)
              );
              if (this.tileSources.length) {
                this.open(this.tileSources[this._sequenceIndex]);
                if (this.showReferenceStrip) {
                  this.addReferenceStrip();
                }
              }
              this._updateSequenceButtons(this._sequenceIndex);
              return this;
            }
            if (!e.isArray(p)) {
              p = [p];
            }
            if (!p.length) {
              return this;
            }
            this._opening = true;
            var I = p.length;
            var k = 0;
            var J = 0;
            var X;
            var pe = function () {
              if (k + J === I) {
                if (k) {
                  if (R._firstOpen || !R.preserveViewport) {
                    R.viewport.goHome(true);
                    R.viewport.update();
                  }
                  R._firstOpen = false;
                  var oe = p[0];
                  if (oe.tileSource) {
                    oe = oe.tileSource;
                  }
                  if (R.overlays && !R.preserveOverlays) {
                    for (var Ce = 0; Ce < R.overlays.length; Ce++) {
                      R.currentOverlays[Ce] = a(R, R.overlays[Ce]);
                    }
                  }
                  R._drawOverlays();
                  R._opening = false;
                  R.raiseEvent('open', { source: oe });
                } else {
                  R._opening = false;
                  R.raiseEvent('open-failed', X);
                }
              }
            };
            var _e = function (oe) {
              if (!e.isPlainObject(oe) || !oe.tileSource) {
                oe = { tileSource: oe };
              }
              if (oe.index !== void 0) {
                e.console.error(
                  '[Viewer.open] setting indexes here is not supported; use addTiledImage instead'
                );
                delete oe.index;
              }
              if (oe.collectionImmediately === void 0) {
                oe.collectionImmediately = true;
              }
              var Ce = oe.success;
              oe.success = function (He) {
                k++;
                if (oe.tileSource.overlays) {
                  for (var Ft = 0; Ft < oe.tileSource.overlays.length; Ft++) {
                    R.addOverlay(oe.tileSource.overlays[Ft]);
                  }
                }
                if (Ce) {
                  Ce(He);
                }
                pe();
              };
              var he = oe.error;
              oe.error = function (He) {
                J++;
                if (!X) {
                  X = He;
                }
                if (he) {
                  he(He);
                }
                pe();
              };
              R.addTiledImage(oe);
            };
            for (var ve = 0; ve < p.length; ve++) {
              _e(p[ve]);
            }
            return this;
          },
          close: function () {
            if (n[this.hash]) {
              this._opening = false;
              if (this.navigator) {
                this.navigator.close();
              }
              if (!this.preserveOverlays) {
                this.clearOverlays();
                this.overlaysContainer.innerHTML = '';
              }
              n[this.hash].animating = false;
              this.world.removeAll();
              this.imageLoader.clear();
              this.raiseEvent('close');
              return this;
            } else {
              return this;
            }
          },
          destroy: function () {
            if (n[this.hash]) {
              this._removeUpdatePixelDensityRatioEvent();
              this.close();
              this.clearOverlays();
              this.overlaysContainer.innerHTML = '';
              if (this.referenceStrip) {
                this.referenceStrip.destroy();
                this.referenceStrip = null;
              }
              if (this._updateRequestId !== null) {
                e.cancelAnimationFrame(this._updateRequestId);
                this._updateRequestId = null;
              }
              if (this.drawer) {
                this.drawer.destroy();
              }
              if (this.navigator) {
                this.navigator.destroy();
                n[this.navigator.hash] = null;
                delete n[this.navigator.hash];
                this.navigator = null;
              }
              this.removeAllHandlers();
              if (this.buttonGroup) {
                this.buttonGroup.destroy();
              } else if (this.customButtons) {
                while (this.customButtons.length) {
                  this.customButtons.pop().destroy();
                }
              }
              if (this.paging) {
                this.paging.destroy();
              }
              if (this.element) {
                while (this.element.firstChild) {
                  this.element.removeChild(this.element.firstChild);
                }
              }
              this.container.onsubmit = null;
              this.clearControls();
              if (this.innerTracker) {
                this.innerTracker.destroy();
              }
              if (this.outerTracker) {
                this.outerTracker.destroy();
              }
              n[this.hash] = null;
              delete n[this.hash];
              this.canvas = null;
              this.container = null;
              e._viewers.delete(this.element);
              this.element = null;
            }
          },
          isMouseNavEnabled: function () {
            return this.innerTracker.isTracking();
          },
          setMouseNavEnabled: function (p) {
            this.innerTracker.setTracking(p);
            this.outerTracker.setTracking(p);
            this.raiseEvent('mouse-enabled', { enabled: p });
            return this;
          },
          areControlsEnabled: function () {
            var p = this.controls.length;
            for (var _ = 0; _ < this.controls.length; _++) {
              p = p && this.controls[_].isVisible();
            }
            return p;
          },
          setControlsEnabled: function (p) {
            if (p) {
              g(this);
            } else {
              h(this);
            }
            this.raiseEvent('controls-enabled', { enabled: p });
            return this;
          },
          setDebugMode: function (p) {
            for (var _ = 0; _ < this.world.getItemCount(); _++) {
              this.world.getItemAt(_).debugMode = p;
            }
            this.debugMode = p;
            this.forceRedraw();
          },
          isFullPage: function () {
            return n[this.hash].fullPage;
          },
          setFullPage: function (p) {
            var _ = document.body;
            var R = _.style;
            var I = document.documentElement.style;
            var k = this;
            var J;
            var X;
            if (p === this.isFullPage()) {
              return this;
            }
            var pe = { fullPage: p, preventDefaultAction: false };
            this.raiseEvent('pre-full-page', pe);
            if (pe.preventDefaultAction) {
              return this;
            }
            if (p) {
              this.elementSize = e.getElementSize(this.element);
              this.pageScroll = e.getPageScroll();
              this.elementMargin = this.element.style.margin;
              this.element.style.margin = '0';
              this.elementPadding = this.element.style.padding;
              this.element.style.padding = '0';
              this.bodyMargin = R.margin;
              this.docMargin = I.margin;
              R.margin = '0';
              I.margin = '0';
              this.bodyPadding = R.padding;
              this.docPadding = I.padding;
              R.padding = '0';
              I.padding = '0';
              this.bodyWidth = R.width;
              this.docWidth = I.width;
              R.width = '100%';
              I.width = '100%';
              this.bodyHeight = R.height;
              this.docHeight = I.height;
              R.height = '100%';
              I.height = '100%';
              this.bodyDisplay = R.display;
              R.display = 'block';
              this.previousBody = [];
              n[this.hash].prevElementParent = this.element.parentNode;
              n[this.hash].prevNextSibling = this.element.nextSibling;
              n[this.hash].prevElementWidth = this.element.style.width;
              n[this.hash].prevElementHeight = this.element.style.height;
              J = _.childNodes.length;
              for (X = 0; X < J; X++) {
                this.previousBody.push(_.childNodes[0]);
                _.removeChild(_.childNodes[0]);
              }
              if (this.toolbar && this.toolbar.element) {
                this.toolbar.parentNode = this.toolbar.element.parentNode;
                this.toolbar.nextSibling = this.toolbar.element.nextSibling;
                _.appendChild(this.toolbar.element);
                e.addClass(this.toolbar.element, 'fullpage');
              }
              e.addClass(this.element, 'fullpage');
              _.appendChild(this.element);
              this.element.style.height = e.getWindowSize().y + 'px';
              this.element.style.width = e.getWindowSize().x + 'px';
              if (this.toolbar && this.toolbar.element) {
                this.element.style.height =
                  e.getElementSize(this.element).y -
                  e.getElementSize(this.toolbar.element).y +
                  'px';
              }
              n[this.hash].fullPage = true;
              e.delegate(this, le)({});
            } else {
              this.element.style.margin = this.elementMargin;
              this.element.style.padding = this.elementPadding;
              R.margin = this.bodyMargin;
              I.margin = this.docMargin;
              R.padding = this.bodyPadding;
              I.padding = this.docPadding;
              R.width = this.bodyWidth;
              I.width = this.docWidth;
              R.height = this.bodyHeight;
              I.height = this.docHeight;
              R.display = this.bodyDisplay;
              _.removeChild(this.element);
              J = this.previousBody.length;
              for (X = 0; X < J; X++) {
                _.appendChild(this.previousBody.shift());
              }
              e.removeClass(this.element, 'fullpage');
              n[this.hash].prevElementParent.insertBefore(
                this.element,
                n[this.hash].prevNextSibling
              );
              if (this.toolbar && this.toolbar.element) {
                _.removeChild(this.toolbar.element);
                e.removeClass(this.toolbar.element, 'fullpage');
                this.toolbar.parentNode.insertBefore(
                  this.toolbar.element,
                  this.toolbar.nextSibling
                );
                delete this.toolbar.parentNode;
                delete this.toolbar.nextSibling;
              }
              this.element.style.width = n[this.hash].prevElementWidth;
              this.element.style.height = n[this.hash].prevElementHeight;
              var _e = 0;
              var ve = function () {
                e.setPageScroll(k.pageScroll);
                var oe = e.getPageScroll();
                _e++;
                if (
                  _e < 10 &&
                  (oe.x !== k.pageScroll.x || oe.y !== k.pageScroll.y)
                ) {
                  e.requestAnimationFrame(ve);
                }
              };
              e.requestAnimationFrame(ve);
              n[this.hash].fullPage = false;
              e.delegate(this, re)({});
            }
            if (this.navigator && this.viewport) {
              this.navigator.update(this.viewport);
            }
            this.raiseEvent('full-page', { fullPage: p });
            return this;
          },
          setFullScreen: function (p) {
            var _ = this;
            if (!e.supportsFullScreen) {
              return this.setFullPage(p);
            }
            if (e.isFullScreen() === p) {
              return this;
            }
            var R = { fullScreen: p, preventDefaultAction: false };
            this.raiseEvent('pre-full-screen', R);
            if (R.preventDefaultAction) {
              return this;
            }
            if (p) {
              this.setFullPage(true);
              if (!this.isFullPage()) {
                return this;
              }
              this.fullPageStyleWidth = this.element.style.width;
              this.fullPageStyleHeight = this.element.style.height;
              this.element.style.width = '100%';
              this.element.style.height = '100%';
              var I = function () {
                var k = e.isFullScreen();
                if (!k) {
                  e.removeEvent(document, e.fullScreenEventName, I);
                  e.removeEvent(document, e.fullScreenErrorEventName, I);
                  _.setFullPage(false);
                  if (_.isFullPage()) {
                    _.element.style.width = _.fullPageStyleWidth;
                    _.element.style.height = _.fullPageStyleHeight;
                  }
                }
                if (_.navigator && _.viewport) {
                  setTimeout(function () {
                    _.navigator.update(_.viewport);
                  });
                }
                _.raiseEvent('full-screen', { fullScreen: k });
              };
              e.addEvent(document, e.fullScreenEventName, I);
              e.addEvent(document, e.fullScreenErrorEventName, I);
              e.requestFullScreen(document.body);
            } else {
              e.exitFullScreen();
            }
            return this;
          },
          isVisible: function () {
            return this.container.style.visibility !== 'hidden';
          },
          setVisible: function (p) {
            this.container.style.visibility = p ? '' : 'hidden';
            this.raiseEvent('visible', { visible: p });
            return this;
          },
          addTiledImage: function (p) {
            function I(X) {
              for (var pe = 0; pe < _._loadQueue.length; pe++) {
                if (_._loadQueue[pe] === R) {
                  _._loadQueue.splice(pe, 1);
                  break;
                }
              }
              if (_._loadQueue.length === 0) {
                k(R);
              }
              _.raiseEvent('add-item-failed', X);
              if (p.error) {
                p.error(X);
              }
            }
            function k(X) {
              if (_.collectionMode) {
                _.world.arrange({
                  immediately: X.options.collectionImmediately,
                  rows: _.collectionRows,
                  columns: _.collectionColumns,
                  layout: _.collectionLayout,
                  tileSize: _.collectionTileSize,
                  tileMargin: _.collectionTileMargin,
                });
                _.world.setAutoRefigureSizes(true);
              }
            }
            function J() {
              var X;
              var pe;
              for (
                var _e;
                _._loadQueue.length && ((X = _._loadQueue[0]), !!X.tileSource);

              ) {
                _._loadQueue.splice(0, 1);
                if (X.options.replace) {
                  var ve = _.world.getIndexOfItem(X.options.replaceItem);
                  if (ve !== -1) {
                    X.options.index = ve;
                  }
                  _.world.removeItem(X.options.replaceItem);
                }
                pe = new e.TiledImage({
                  viewer: _,
                  source: X.tileSource,
                  viewport: _.viewport,
                  drawer: _.drawer,
                  tileCache: _.tileCache,
                  imageLoader: _.imageLoader,
                  x: X.options.x,
                  y: X.options.y,
                  width: X.options.width,
                  height: X.options.height,
                  fitBounds: X.options.fitBounds,
                  fitBoundsPlacement: X.options.fitBoundsPlacement,
                  clip: X.options.clip,
                  placeholderFillStyle: X.options.placeholderFillStyle,
                  opacity: X.options.opacity,
                  preload: X.options.preload,
                  degrees: X.options.degrees,
                  flipped: X.options.flipped,
                  compositeOperation: X.options.compositeOperation,
                  springStiffness: _.springStiffness,
                  animationTime: _.animationTime,
                  minZoomImageRatio: _.minZoomImageRatio,
                  wrapHorizontal: _.wrapHorizontal,
                  wrapVertical: _.wrapVertical,
                  immediateRender: _.immediateRender,
                  blendTime: _.blendTime,
                  alwaysBlend: _.alwaysBlend,
                  minPixelRatio: _.minPixelRatio,
                  smoothTileEdgesMinZoom: _.smoothTileEdgesMinZoom,
                  iOSDevice: _.iOSDevice,
                  crossOriginPolicy: X.options.crossOriginPolicy,
                  ajaxWithCredentials: X.options.ajaxWithCredentials,
                  loadTilesWithAjax: X.options.loadTilesWithAjax,
                  ajaxHeaders: X.options.ajaxHeaders,
                  debugMode: _.debugMode,
                });
                if (_.collectionMode) {
                  _.world.setAutoRefigureSizes(false);
                }
                if (_.navigator) {
                  _e = e.extend({}, X.options, {
                    replace: false,
                    originalTiledImage: pe,
                    tileSource: X.tileSource,
                  });
                  _.navigator.addTiledImage(_e);
                }
                _.world.addItem(pe, { index: X.options.index });
                if (_._loadQueue.length === 0) {
                  k(X);
                }
                if (_.world.getItemCount() === 1 && !_.preserveViewport) {
                  _.viewport.goHome(true);
                }
                if (X.options.success) {
                  X.options.success({ item: pe });
                }
              }
            }
            e.console.assert(p, '[Viewer.addTiledImage] options is required');
            e.console.assert(
              p.tileSource,
              '[Viewer.addTiledImage] options.tileSource is required'
            );
            e.console.assert(
              !p.replace ||
                (p.index > -1 && p.index < this.world.getItemCount()),
              '[Viewer.addTiledImage] if options.replace is used, options.index must be a valid index in Viewer.world'
            );
            var _ = this;
            if (p.replace) {
              p.replaceItem = _.world.getItemAt(p.index);
            }
            this._hideMessage();
            if (p.placeholderFillStyle === void 0) {
              p.placeholderFillStyle = this.placeholderFillStyle;
            }
            if (p.opacity === void 0) {
              p.opacity = this.opacity;
            }
            if (p.preload === void 0) {
              p.preload = this.preload;
            }
            if (p.compositeOperation === void 0) {
              p.compositeOperation = this.compositeOperation;
            }
            if (p.crossOriginPolicy === void 0) {
              p.crossOriginPolicy =
                p.tileSource.crossOriginPolicy !== void 0
                  ? p.tileSource.crossOriginPolicy
                  : this.crossOriginPolicy;
            }
            if (p.ajaxWithCredentials === void 0) {
              p.ajaxWithCredentials = this.ajaxWithCredentials;
            }
            if (p.loadTilesWithAjax === void 0) {
              p.loadTilesWithAjax = this.loadTilesWithAjax;
            }
            if (p.ajaxHeaders === void 0 || p.ajaxHeaders === null) {
              p.ajaxHeaders = this.ajaxHeaders;
            } else if (
              e.isPlainObject(p.ajaxHeaders) &&
              e.isPlainObject(this.ajaxHeaders)
            ) {
              p.ajaxHeaders = e.extend({}, this.ajaxHeaders, p.ajaxHeaders);
            }
            var R = { options: p };
            if (e.isArray(p.tileSource)) {
              setTimeout(function () {
                I({
                  message:
                    '[Viewer.addTiledImage] Sequences can not be added; add them one at a time instead.',
                  source: p.tileSource,
                  options: p,
                });
              });
              return;
            }
            this._loadQueue.push(R);
            s(
              this,
              p.tileSource,
              p,
              function (X) {
                R.tileSource = X;
                J();
              },
              function (X) {
                X.options = p;
                I(X);
                J();
              }
            );
          },
          addSimpleImage: function (p) {
            e.console.assert(p, '[Viewer.addSimpleImage] options is required');
            e.console.assert(
              p.url,
              '[Viewer.addSimpleImage] options.url is required'
            );
            var _ = e.extend({}, p, {
              tileSource: { type: 'image', url: p.url },
            });
            delete _.url;
            this.addTiledImage(_);
          },
          addLayer: function (p) {
            var _ = this;
            e.console.error(
              '[Viewer.addLayer] this function is deprecated; use Viewer.addTiledImage() instead.'
            );
            var R = e.extend({}, p, {
              success: function (I) {
                _.raiseEvent('add-layer', { options: p, drawer: I.item });
              },
              error: function (I) {
                _.raiseEvent('add-layer-failed', I);
              },
            });
            this.addTiledImage(R);
            return this;
          },
          getLayerAtLevel: function (p) {
            e.console.error(
              '[Viewer.getLayerAtLevel] this function is deprecated; use World.getItemAt() instead.'
            );
            return this.world.getItemAt(p);
          },
          getLevelOfLayer: function (p) {
            e.console.error(
              '[Viewer.getLevelOfLayer] this function is deprecated; use World.getIndexOfItem() instead.'
            );
            return this.world.getIndexOfItem(p);
          },
          getLayersCount: function () {
            e.console.error(
              '[Viewer.getLayersCount] this function is deprecated; use World.getItemCount() instead.'
            );
            return this.world.getItemCount();
          },
          setLayerLevel: function (p, _) {
            e.console.error(
              '[Viewer.setLayerLevel] this function is deprecated; use World.setItemIndex() instead.'
            );
            return this.world.setItemIndex(p, _);
          },
          removeLayer: function (p) {
            e.console.error(
              '[Viewer.removeLayer] this function is deprecated; use World.removeItem() instead.'
            );
            return this.world.removeItem(p);
          },
          forceRedraw: function () {
            n[this.hash].forceRedraw = true;
            return this;
          },
          bindSequenceControls: function () {
            var p = e.delegate(this, y);
            var _ = e.delegate(this, x);
            var R = e.delegate(this, this.goToNextPage);
            var I = e.delegate(this, this.goToPreviousPage);
            var k = this.navImages;
            var J = true;
            if (this.showSequenceControl) {
              if (this.previousButton || this.nextButton) {
                J = false;
              }
              this.previousButton = new e.Button({
                element: this.previousButton
                  ? e.getElement(this.previousButton)
                  : null,
                clickTimeThreshold: this.clickTimeThreshold,
                clickDistThreshold: this.clickDistThreshold,
                tooltip: e.getString('Tooltips.PreviousPage'),
                srcRest: q(this.prefixUrl, k.previous.REST),
                srcGroup: q(this.prefixUrl, k.previous.GROUP),
                srcHover: q(this.prefixUrl, k.previous.HOVER),
                srcDown: q(this.prefixUrl, k.previous.DOWN),
                onRelease: I,
                onFocus: p,
                onBlur: _,
              });
              this.nextButton = new e.Button({
                element: this.nextButton ? e.getElement(this.nextButton) : null,
                clickTimeThreshold: this.clickTimeThreshold,
                clickDistThreshold: this.clickDistThreshold,
                tooltip: e.getString('Tooltips.NextPage'),
                srcRest: q(this.prefixUrl, k.next.REST),
                srcGroup: q(this.prefixUrl, k.next.GROUP),
                srcHover: q(this.prefixUrl, k.next.HOVER),
                srcDown: q(this.prefixUrl, k.next.DOWN),
                onRelease: R,
                onFocus: p,
                onBlur: _,
              });
              if (!this.navPrevNextWrap) {
                this.previousButton.disable();
              }
              if (!this.tileSources || !this.tileSources.length) {
                this.nextButton.disable();
              }
              if (J) {
                this.paging = new e.ButtonGroup({
                  buttons: [this.previousButton, this.nextButton],
                  clickTimeThreshold: this.clickTimeThreshold,
                  clickDistThreshold: this.clickDistThreshold,
                });
                this.pagingControl = this.paging.element;
                if (this.toolbar) {
                  this.toolbar.addControl(this.pagingControl, {
                    anchor: e.ControlAnchor.BOTTOM_RIGHT,
                  });
                } else {
                  this.addControl(this.pagingControl, {
                    anchor:
                      this.sequenceControlAnchor || e.ControlAnchor.TOP_LEFT,
                  });
                }
              }
            }
            return this;
          },
          bindStandardControls: function () {
            var p = e.delegate(this, Le);
            var _ = e.delegate(this, V);
            var R = e.delegate(this, G);
            var I = e.delegate(this, F);
            var k = e.delegate(this, $);
            var J = e.delegate(this, Se);
            var X = e.delegate(this, ge);
            var pe = e.delegate(this, tt);
            var _e = e.delegate(this, nt);
            var ve = e.delegate(this, it);
            var oe = e.delegate(this, y);
            var Ce = e.delegate(this, x);
            var he = this.navImages;
            var He = [];
            var Ft = true;
            if (this.showNavigationControl) {
              if (
                this.zoomInButton ||
                this.zoomOutButton ||
                this.homeButton ||
                this.fullPageButton ||
                this.rotateLeftButton ||
                this.rotateRightButton ||
                this.flipButton
              ) {
                Ft = false;
              }
              if (this.showZoomControl) {
                He.push(
                  (this.zoomInButton = new e.Button({
                    element: this.zoomInButton
                      ? e.getElement(this.zoomInButton)
                      : null,
                    clickTimeThreshold: this.clickTimeThreshold,
                    clickDistThreshold: this.clickDistThreshold,
                    tooltip: e.getString('Tooltips.ZoomIn'),
                    srcRest: q(this.prefixUrl, he.zoomIn.REST),
                    srcGroup: q(this.prefixUrl, he.zoomIn.GROUP),
                    srcHover: q(this.prefixUrl, he.zoomIn.HOVER),
                    srcDown: q(this.prefixUrl, he.zoomIn.DOWN),
                    onPress: p,
                    onRelease: _,
                    onClick: R,
                    onEnter: p,
                    onExit: _,
                    onFocus: oe,
                    onBlur: Ce,
                  }))
                );
                He.push(
                  (this.zoomOutButton = new e.Button({
                    element: this.zoomOutButton
                      ? e.getElement(this.zoomOutButton)
                      : null,
                    clickTimeThreshold: this.clickTimeThreshold,
                    clickDistThreshold: this.clickDistThreshold,
                    tooltip: e.getString('Tooltips.ZoomOut'),
                    srcRest: q(this.prefixUrl, he.zoomOut.REST),
                    srcGroup: q(this.prefixUrl, he.zoomOut.GROUP),
                    srcHover: q(this.prefixUrl, he.zoomOut.HOVER),
                    srcDown: q(this.prefixUrl, he.zoomOut.DOWN),
                    onPress: I,
                    onRelease: _,
                    onClick: k,
                    onEnter: I,
                    onExit: _,
                    onFocus: oe,
                    onBlur: Ce,
                  }))
                );
              }
              if (this.showHomeControl) {
                He.push(
                  (this.homeButton = new e.Button({
                    element: this.homeButton
                      ? e.getElement(this.homeButton)
                      : null,
                    clickTimeThreshold: this.clickTimeThreshold,
                    clickDistThreshold: this.clickDistThreshold,
                    tooltip: e.getString('Tooltips.Home'),
                    srcRest: q(this.prefixUrl, he.home.REST),
                    srcGroup: q(this.prefixUrl, he.home.GROUP),
                    srcHover: q(this.prefixUrl, he.home.HOVER),
                    srcDown: q(this.prefixUrl, he.home.DOWN),
                    onRelease: J,
                    onFocus: oe,
                    onBlur: Ce,
                  }))
                );
              }
              if (this.showFullPageControl) {
                He.push(
                  (this.fullPageButton = new e.Button({
                    element: this.fullPageButton
                      ? e.getElement(this.fullPageButton)
                      : null,
                    clickTimeThreshold: this.clickTimeThreshold,
                    clickDistThreshold: this.clickDistThreshold,
                    tooltip: e.getString('Tooltips.FullPage'),
                    srcRest: q(this.prefixUrl, he.fullpage.REST),
                    srcGroup: q(this.prefixUrl, he.fullpage.GROUP),
                    srcHover: q(this.prefixUrl, he.fullpage.HOVER),
                    srcDown: q(this.prefixUrl, he.fullpage.DOWN),
                    onRelease: X,
                    onFocus: oe,
                    onBlur: Ce,
                  }))
                );
              }
              if (this.showRotationControl) {
                He.push(
                  (this.rotateLeftButton = new e.Button({
                    element: this.rotateLeftButton
                      ? e.getElement(this.rotateLeftButton)
                      : null,
                    clickTimeThreshold: this.clickTimeThreshold,
                    clickDistThreshold: this.clickDistThreshold,
                    tooltip: e.getString('Tooltips.RotateLeft'),
                    srcRest: q(this.prefixUrl, he.rotateleft.REST),
                    srcGroup: q(this.prefixUrl, he.rotateleft.GROUP),
                    srcHover: q(this.prefixUrl, he.rotateleft.HOVER),
                    srcDown: q(this.prefixUrl, he.rotateleft.DOWN),
                    onRelease: pe,
                    onFocus: oe,
                    onBlur: Ce,
                  }))
                );
                He.push(
                  (this.rotateRightButton = new e.Button({
                    element: this.rotateRightButton
                      ? e.getElement(this.rotateRightButton)
                      : null,
                    clickTimeThreshold: this.clickTimeThreshold,
                    clickDistThreshold: this.clickDistThreshold,
                    tooltip: e.getString('Tooltips.RotateRight'),
                    srcRest: q(this.prefixUrl, he.rotateright.REST),
                    srcGroup: q(this.prefixUrl, he.rotateright.GROUP),
                    srcHover: q(this.prefixUrl, he.rotateright.HOVER),
                    srcDown: q(this.prefixUrl, he.rotateright.DOWN),
                    onRelease: _e,
                    onFocus: oe,
                    onBlur: Ce,
                  }))
                );
              }
              if (this.showFlipControl) {
                He.push(
                  (this.flipButton = new e.Button({
                    element: this.flipButton
                      ? e.getElement(this.flipButton)
                      : null,
                    clickTimeThreshold: this.clickTimeThreshold,
                    clickDistThreshold: this.clickDistThreshold,
                    tooltip: e.getString('Tooltips.Flip'),
                    srcRest: q(this.prefixUrl, he.flip.REST),
                    srcGroup: q(this.prefixUrl, he.flip.GROUP),
                    srcHover: q(this.prefixUrl, he.flip.HOVER),
                    srcDown: q(this.prefixUrl, he.flip.DOWN),
                    onRelease: ve,
                    onFocus: oe,
                    onBlur: Ce,
                  }))
                );
              }
              if (Ft) {
                this.buttonGroup = new e.ButtonGroup({
                  buttons: He,
                  clickTimeThreshold: this.clickTimeThreshold,
                  clickDistThreshold: this.clickDistThreshold,
                });
                this.navControl = this.buttonGroup.element;
                this.addHandler('open', e.delegate(this, ae));
                if (this.toolbar) {
                  this.toolbar.addControl(this.navControl, {
                    anchor:
                      this.navigationControlAnchor || e.ControlAnchor.TOP_LEFT,
                  });
                } else {
                  this.addControl(this.navControl, {
                    anchor:
                      this.navigationControlAnchor || e.ControlAnchor.TOP_LEFT,
                  });
                }
              } else {
                this.customButtons = He;
              }
            }
            return this;
          },
          currentPage: function () {
            return this._sequenceIndex;
          },
          goToPage: function (p) {
            if (this.tileSources && p >= 0 && p < this.tileSources.length) {
              this._sequenceIndex = p;
              this._updateSequenceButtons(p);
              this.open(this.tileSources[p]);
              if (this.referenceStrip) {
                this.referenceStrip.setFocus(p);
              }
              this.raiseEvent('page', { page: p });
            }
            return this;
          },
          addOverlay: function (p, _, R, I) {
            var k;
            if (e.isPlainObject(p)) {
              k = p;
            } else {
              k = { element: p, location: _, placement: R, onDraw: I };
            }
            p = e.getElement(k.element);
            if (l(this.currentOverlays, p) >= 0) {
              return this;
            }
            var J = a(this, k);
            this.currentOverlays.push(J);
            J.drawHTML(this.overlaysContainer, this.viewport);
            this.raiseEvent('add-overlay', {
              element: p,
              location: k.location,
              placement: k.placement,
            });
            return this;
          },
          updateOverlay: function (p, _, R) {
            p = e.getElement(p);
            var I = l(this.currentOverlays, p);
            if (I >= 0) {
              this.currentOverlays[I].update(_, R);
              n[this.hash].forceRedraw = true;
              this.raiseEvent('update-overlay', {
                element: p,
                location: _,
                placement: R,
              });
            }
            return this;
          },
          removeOverlay: function (p) {
            p = e.getElement(p);
            var _ = l(this.currentOverlays, p);
            if (_ >= 0) {
              this.currentOverlays[_].destroy();
              this.currentOverlays.splice(_, 1);
              n[this.hash].forceRedraw = true;
              this.raiseEvent('remove-overlay', { element: p });
            }
            return this;
          },
          clearOverlays: function () {
            while (this.currentOverlays.length > 0) {
              this.currentOverlays.pop().destroy();
            }
            n[this.hash].forceRedraw = true;
            this.raiseEvent('clear-overlay', {});
            return this;
          },
          getOverlayById: function (p) {
            p = e.getElement(p);
            var _ = l(this.currentOverlays, p);
            if (_ >= 0) {
              return this.currentOverlays[_];
            } else {
              return null;
            }
          },
          _updateSequenceButtons: function (p) {
            if (this.nextButton) {
              if (!this.tileSources || this.tileSources.length - 1 === p) {
                if (!this.navPrevNextWrap) {
                  this.nextButton.disable();
                }
              } else {
                this.nextButton.enable();
              }
            }
            if (this.previousButton) {
              if (p > 0) {
                this.previousButton.enable();
              } else if (!this.navPrevNextWrap) {
                this.previousButton.disable();
              }
            }
          },
          _showMessage: function (p) {
            this._hideMessage();
            var _ = e.makeNeutralElement('div');
            _.appendChild(document.createTextNode(p));
            this.messageDiv = e.makeCenteredNode(_);
            e.addClass(this.messageDiv, 'openseadragon-message');
            this.container.appendChild(this.messageDiv);
          },
          _hideMessage: function () {
            var p = this.messageDiv;
            if (p) {
              p.parentNode.removeChild(p);
              delete this.messageDiv;
            }
          },
          gestureSettingsByDeviceType: function (p) {
            switch (p) {
              case 'mouse':
                return this.gestureSettingsMouse;
              case 'touch':
                return this.gestureSettingsTouch;
              case 'pen':
                return this.gestureSettingsPen;
              default:
                return this.gestureSettingsUnknown;
            }
          },
          _drawOverlays: function () {
            var _ = this.currentOverlays.length;
            for (var p = 0; p < _; p++) {
              this.currentOverlays[p].drawHTML(
                this.overlaysContainer,
                this.viewport
              );
            }
          },
          _cancelPendingImages: function () {
            this._loadQueue = [];
          },
          removeReferenceStrip: function () {
            this.showReferenceStrip = false;
            if (this.referenceStrip) {
              this.referenceStrip.destroy();
              this.referenceStrip = null;
            }
          },
          addReferenceStrip: function () {
            this.showReferenceStrip = true;
            if (this.sequenceMode) {
              if (this.referenceStrip) {
                return;
              }
              if (this.tileSources.length && this.tileSources.length > 1) {
                this.referenceStrip = new e.ReferenceStrip({
                  id: this.referenceStripElement,
                  position: this.referenceStripPosition,
                  sizeRatio: this.referenceStripSizeRatio,
                  scroll: this.referenceStripScroll,
                  height: this.referenceStripHeight,
                  width: this.referenceStripWidth,
                  tileSources: this.tileSources,
                  prefixUrl: this.prefixUrl,
                  useCanvas: this.useCanvas,
                  viewer: this,
                });
                this.referenceStrip.setFocus(this._sequenceIndex);
              }
            } else {
              e.console.warn(
                'Attempting to display a reference strip while "sequenceMode" is off.'
              );
            }
          },
          _addUpdatePixelDensityRatioEvent: function () {
            this._updatePixelDensityRatioBind =
              this._updatePixelDensityRatio.bind(this);
            e.addEvent(window, 'resize', this._updatePixelDensityRatioBind);
          },
          _removeUpdatePixelDensityRatioEvent: function () {
            e.removeEvent(window, 'resize', this._updatePixelDensityRatioBind);
          },
          _updatePixelDensityRatio: function () {
            var p = e.pixelDensityRatio;
            var _ = e.getCurrentPixelDensityRatio();
            if (p !== _) {
              e.pixelDensityRatio = _;
              this.world.resetItems();
              this.forceRedraw();
            }
          },
          goToPreviousPage: function () {
            var p = this._sequenceIndex - 1;
            if (this.navPrevNextWrap && p < 0) {
              p += this.tileSources.length;
            }
            this.goToPage(p);
          },
          goToNextPage: function () {
            var p = this._sequenceIndex + 1;
            if (this.navPrevNextWrap && p >= this.tileSources.length) {
              p = 0;
            }
            this.goToPage(p);
          },
        }
      );
    })();
    (function () {
      function n(u) {
        var c = {
          tracker: u.eventSource,
          position: u.position,
          quick: u.quick,
          shift: u.shift,
          originalEvent: u.originalEvent,
          preventDefaultAction: false,
        };
        this.viewer.raiseEvent('navigator-click', c);
        if (
          !c.preventDefaultAction &&
          u.quick &&
          this.viewer.viewport &&
          (this.panVertical || this.panHorizontal)
        ) {
          if (this.viewer.viewport.flipped) {
            u.position.x = this.viewport.getContainerSize().x - u.position.x;
          }
          var h = this.viewport.pointFromPixel(u.position);
          if (this.panVertical) {
            if (!this.panHorizontal) {
              h.x = this.viewer.viewport.getCenter(true).x;
            }
          } else {
            h.y = this.viewer.viewport.getCenter(true).y;
          }
          this.viewer.viewport.panTo(h);
          this.viewer.viewport.applyConstraints();
        }
      }
      function r(u) {
        var c = {
          tracker: u.eventSource,
          position: u.position,
          delta: u.delta,
          speed: u.speed,
          direction: u.direction,
          shift: u.shift,
          originalEvent: u.originalEvent,
          preventDefaultAction: false,
        };
        this.viewer.raiseEvent('navigator-drag', c);
        if (!c.preventDefaultAction && this.viewer.viewport) {
          if (!this.panHorizontal) {
            u.delta.x = 0;
          }
          if (!this.panVertical) {
            u.delta.y = 0;
          }
          if (this.viewer.viewport.flipped) {
            u.delta.x = -u.delta.x;
          }
          this.viewer.viewport.panBy(
            this.viewport.deltaPointsFromPixels(u.delta)
          );
          if (this.viewer.constrainDuringPan) {
            this.viewer.viewport.applyConstraints();
          }
        }
      }
      function o(u) {
        if (u.insideElementPressed && this.viewer.viewport) {
          this.viewer.viewport.applyConstraints();
        }
      }
      function s(u) {
        var c = {
          tracker: u.eventSource,
          position: u.position,
          scroll: u.scroll,
          shift: u.shift,
          originalEvent: u.originalEvent,
          preventDefault: u.preventDefault,
        };
        this.viewer.raiseEvent('navigator-scroll', c);
        u.preventDefault = c.preventDefault;
      }
      function a(u, c) {
        l(u, 'rotate(' + c + 'deg)');
      }
      function l(u, c) {
        u.style.webkitTransform = c;
        u.style.mozTransform = c;
        u.style.msTransform = c;
        u.style.oTransform = c;
        u.style.transform = c;
      }
      var e = t;
      e.Navigator = function (u) {
        function y(b) {
          a(h.displayRegionContainer, b);
          a(h.displayRegion, -b);
          h.viewport.setRotation(b);
        }
        var c = u.viewer;
        var h = this;
        var d;
        var g;
        if (u.id) {
          this.element = document.getElementById(u.id);
          u.controlOptions = {
            anchor: e.ControlAnchor.NONE,
            attachToViewer: false,
            autoFade: false,
          };
        } else {
          u.id = 'navigator-' + e.now();
          this.element = e.makeNeutralElement('div');
          u.controlOptions = {
            anchor: e.ControlAnchor.TOP_RIGHT,
            attachToViewer: true,
            autoFade: u.autoFade,
          };
          if (u.position) {
            if (u.position === 'BOTTOM_RIGHT') {
              u.controlOptions.anchor = e.ControlAnchor.BOTTOM_RIGHT;
            } else if (u.position === 'BOTTOM_LEFT') {
              u.controlOptions.anchor = e.ControlAnchor.BOTTOM_LEFT;
            } else if (u.position === 'TOP_RIGHT') {
              u.controlOptions.anchor = e.ControlAnchor.TOP_RIGHT;
            } else if (u.position === 'TOP_LEFT') {
              u.controlOptions.anchor = e.ControlAnchor.TOP_LEFT;
            } else if (u.position === 'ABSOLUTE') {
              u.controlOptions.anchor = e.ControlAnchor.ABSOLUTE;
              u.controlOptions.top = u.top;
              u.controlOptions.left = u.left;
              u.controlOptions.height = u.height;
              u.controlOptions.width = u.width;
            }
          }
        }
        this.element.id = u.id;
        this.element.className += ' navigator';
        u = e.extend(
          true,
          { sizeRatio: e.DEFAULT_SETTINGS.navigatorSizeRatio },
          u,
          {
            element: this.element,
            tabIndex: -1,
            showNavigator: false,
            mouseNavEnabled: false,
            showNavigationControl: false,
            showSequenceControl: false,
            immediateRender: true,
            blendTime: 0,
            animationTime: 0,
            autoResize: u.autoResize,
            minZoomImageRatio: 1,
            background: u.background,
            opacity: u.opacity,
            borderColor: u.borderColor,
            displayRegionColor: u.displayRegionColor,
          }
        );
        u.minPixelRatio = this.minPixelRatio = c.minPixelRatio;
        e.setElementTouchActionNone(this.element);
        this.borderWidth = 2;
        this.fudge = new e.Point(1, 1);
        this.totalBorderWidths = new e.Point(
          this.borderWidth * 2,
          this.borderWidth * 2
        ).minus(this.fudge);
        if (u.controlOptions.anchor !== e.ControlAnchor.NONE) {
          (function (b, T) {
            b.margin = '0px';
            b.border = T + 'px solid ' + u.borderColor;
            b.padding = '0px';
            b.background = u.background;
            b.opacity = u.opacity;
            b.overflow = 'hidden';
          })(this.element.style, this.borderWidth);
        }
        this.displayRegion = e.makeNeutralElement('div');
        this.displayRegion.id = this.element.id + '-displayregion';
        this.displayRegion.className = 'displayregion';
        (function (b, T) {
          b.position = 'relative';
          b.top = '0px';
          b.left = '0px';
          b.fontSize = '0px';
          b.overflow = 'hidden';
          b.border = T + 'px solid ' + u.displayRegionColor;
          b.margin = '0px';
          b.padding = '0px';
          b.background = 'transparent';
          b.float = 'left';
          b.cssFloat = 'left';
          b.styleFloat = 'left';
          b.zIndex = 999999999;
          b.cursor = 'default';
        })(this.displayRegion.style, this.borderWidth);
        e.setElementPointerEventsNone(this.displayRegion);
        e.setElementTouchActionNone(this.displayRegion);
        this.displayRegionContainer = e.makeNeutralElement('div');
        this.displayRegionContainer.id =
          this.element.id + '-displayregioncontainer';
        this.displayRegionContainer.className = 'displayregioncontainer';
        this.displayRegionContainer.style.width = '100%';
        this.displayRegionContainer.style.height = '100%';
        e.setElementPointerEventsNone(this.displayRegionContainer);
        e.setElementTouchActionNone(this.displayRegionContainer);
        c.addControl(this.element, u.controlOptions);
        this._resizeWithViewer =
          u.controlOptions.anchor !== e.ControlAnchor.ABSOLUTE &&
          u.controlOptions.anchor !== e.ControlAnchor.NONE;
        if (u.width && u.height) {
          this.setWidth(u.width);
          this.setHeight(u.height);
        } else if (this._resizeWithViewer) {
          d = e.getElementSize(c.element);
          this.element.style.height = Math.round(d.y * u.sizeRatio) + 'px';
          this.element.style.width = Math.round(d.x * u.sizeRatio) + 'px';
          this.oldViewerSize = d;
          g = e.getElementSize(this.element);
          this.elementArea = g.x * g.y;
        }
        this.oldContainerSize = new e.Point(0, 0);
        e.Viewer.apply(this, [u]);
        this.displayRegionContainer.appendChild(this.displayRegion);
        this.element
          .getElementsByTagName('div')[0]
          .appendChild(this.displayRegionContainer);
        if (u.navigatorRotate) {
          var x = u.viewer.viewport
            ? u.viewer.viewport.getRotation()
            : u.viewer.degrees || 0;
          y(x);
          u.viewer.addHandler('rotate', function (b) {
            y(b.degrees);
          });
        }
        this.innerTracker.destroy();
        this.innerTracker = new e.MouseTracker({
          userData: 'Navigator.innerTracker',
          element: this.element,
          dragHandler: e.delegate(this, r),
          clickHandler: e.delegate(this, n),
          releaseHandler: e.delegate(this, o),
          scrollHandler: e.delegate(this, s),
          preProcessEventHandler: function (b) {
            if (b.eventType === 'wheel') {
              b.preventDefault = true;
            }
          },
        });
        this.outerTracker.userData = 'Navigator.outerTracker';
        e.setElementPointerEventsNone(this.canvas);
        e.setElementPointerEventsNone(this.container);
        this.addHandler('reset-size', function () {
          if (h.viewport) {
            h.viewport.goHome(true);
          }
        });
        c.world.addHandler('item-index-change', function (b) {
          window.setTimeout(function () {
            var T = h.world.getItemAt(b.previousIndex);
            h.world.setItemIndex(T, b.newIndex);
          }, 1);
        });
        c.world.addHandler('remove-item', function (b) {
          var T = b.item;
          var f = h._getMatchingItem(T);
          if (f) {
            h.world.removeItem(f);
          }
        });
        this.update(c.viewport);
      };
      e.extend(
        e.Navigator.prototype,
        e.EventSource.prototype,
        e.Viewer.prototype,
        {
          updateSize: function () {
            if (this.viewport) {
              var u = new e.Point(
                this.container.clientWidth === 0
                  ? 1
                  : this.container.clientWidth,
                this.container.clientHeight === 0
                  ? 1
                  : this.container.clientHeight
              );
              if (!u.equals(this.oldContainerSize)) {
                this.viewport.resize(u, true);
                this.viewport.goHome(true);
                this.oldContainerSize = u;
                this.drawer.clear();
                this.world.draw();
              }
            }
          },
          setWidth: function (u) {
            this.width = u;
            this.element.style.width = typeof u == 'number' ? u + 'px' : u;
            this._resizeWithViewer = false;
          },
          setHeight: function (u) {
            this.height = u;
            this.element.style.height = typeof u == 'number' ? u + 'px' : u;
            this._resizeWithViewer = false;
          },
          setFlip: function (u) {
            this.viewport.setFlip(u);
            this.setDisplayTransform(
              this.viewer.viewport.getFlip() ? 'scale(-1,1)' : 'scale(1,1)'
            );
            return this;
          },
          setDisplayTransform: function (u) {
            l(this.displayRegion, u);
            l(this.canvas, u);
            l(this.element, u);
          },
          update: function (u) {
            var h;
            var d;
            var g;
            var y;
            var x;
            var c = e.getElementSize(this.viewer.element);
            if (
              this._resizeWithViewer &&
              c.x &&
              c.y &&
              !c.equals(this.oldViewerSize)
            ) {
              this.oldViewerSize = c;
              if (this.maintainSizeRatio || !this.elementArea) {
                h = c.x * this.sizeRatio;
                d = c.y * this.sizeRatio;
              } else {
                h = Math.sqrt(this.elementArea * (c.x / c.y));
                d = this.elementArea / h;
              }
              this.element.style.width = Math.round(h) + 'px';
              this.element.style.height = Math.round(d) + 'px';
              if (!this.elementArea) {
                this.elementArea = h * d;
              }
              this.updateSize();
            }
            if (u && this.viewport) {
              g = u.getBoundsNoRotate(true);
              y = this.viewport.pixelFromPointNoRotate(g.getTopLeft(), false);
              x = this.viewport
                .pixelFromPointNoRotate(g.getBottomRight(), false)
                .minus(this.totalBorderWidths);
              var b = this.displayRegion.style;
              b.display = this.world.getItemCount() ? 'block' : 'none';
              b.top = Math.round(y.y) + 'px';
              b.left = Math.round(y.x) + 'px';
              var T = Math.abs(y.x - x.x);
              var f = Math.abs(y.y - x.y);
              b.width = Math.round(Math.max(T, 0)) + 'px';
              b.height = Math.round(Math.max(f, 0)) + 'px';
            }
          },
          addTiledImage: function (u) {
            var c = this;
            var h = u.originalTiledImage;
            delete u.original;
            var d = e.extend({}, u, {
              success: function (g) {
                function x() {
                  c._matchBounds(y, h);
                }
                function b() {
                  c._matchOpacity(y, h);
                }
                function T() {
                  c._matchCompositeOperation(y, h);
                }
                var y = g.item;
                y._originalForNavigator = h;
                c._matchBounds(y, h, true);
                c._matchOpacity(y, h);
                c._matchCompositeOperation(y, h);
                h.addHandler('bounds-change', x);
                h.addHandler('clip-change', x);
                h.addHandler('opacity-change', b);
                h.addHandler('composite-operation-change', T);
              },
            });
            return e.Viewer.prototype.addTiledImage.apply(this, [d]);
          },
          destroy: function () {
            return e.Viewer.prototype.destroy.apply(this);
          },
          _getMatchingItem: function (u) {
            var c = this.world.getItemCount();
            var h;
            for (var d = 0; d < c; d++) {
              h = this.world.getItemAt(d);
              if (h._originalForNavigator === u) {
                return h;
              }
            }
            return null;
          },
          _matchBounds: function (u, c, h) {
            var d = c.getBoundsNoRotate();
            u.setPosition(d.getTopLeft(), h);
            u.setWidth(d.width, h);
            u.setRotation(c.getRotation(), h);
            u.setClip(c.getClip());
            u.setFlip(c.getFlip());
          },
          _matchOpacity: function (u, c) {
            u.setOpacity(c.opacity);
          },
          _matchCompositeOperation: function (u, c) {
            u.setCompositeOperation(c.compositeOperation);
          },
        }
      );
    })();
    (function () {
      var e = t;
      var n = {
        Errors: {
          Dzc: "Sorry, we don't support Deep Zoom Collections!",
          Dzi: "Hmm, this doesn't appear to be a valid Deep Zoom Image.",
          Xml: "Hmm, this doesn't appear to be a valid Deep Zoom Image.",
          ImageFormat: "Sorry, we don't support {0}-based Deep Zoom Images.",
          Security:
            'It looks like a security restriction stopped us from loading this Deep Zoom Image.',
          Status: 'This space unintentionally left blank ({0} {1}).',
          OpenFailed: 'Unable to open {0}: {1}',
        },
        Tooltips: {
          FullPage: 'Toggle full page',
          Home: 'Go home',
          ZoomIn: 'Zoom in',
          ZoomOut: 'Zoom out',
          NextPage: 'Next page',
          PreviousPage: 'Previous page',
          RotateLeft: 'Rotate left',
          RotateRight: 'Rotate right',
          Flip: 'Flip Horizontally',
        },
      };
      e.extend(e, {
        getString: function (r) {
          var o = r.split('.');
          var s = null;
          var a = arguments;
          var l = n;
          for (var u = 0; u < o.length - 1; u++) {
            l = l[o[u]] || {};
          }
          s = l[o[u]];
          if (typeof s != 'string') {
            e.console.log('Untranslated source string:', r);
            s = '';
          }
          return s.replace(/\{\d+\}/g, function (c) {
            var h = parseInt(c.match(/\d+/), 10) + 1;
            if (h < a.length) {
              return a[h];
            } else {
              return '';
            }
          });
        },
        setString: function (r, o) {
          var s = r.split('.');
          var a = n;
          for (var l = 0; l < s.length - 1; l++) {
            if (!a[s[l]]) {
              a[s[l]] = {};
            }
            a = a[s[l]];
          }
          a[s[l]] = o;
        },
      });
    })();
    (function () {
      var e = t;
      e.Point = function (n, r) {
        this.x = typeof n == 'number' ? n : 0;
        this.y = typeof r == 'number' ? r : 0;
      };
      e.Point.prototype = {
        clone: function () {
          return new e.Point(this.x, this.y);
        },
        plus: function (n) {
          return new e.Point(this.x + n.x, this.y + n.y);
        },
        minus: function (n) {
          return new e.Point(this.x - n.x, this.y - n.y);
        },
        times: function (n) {
          return new e.Point(this.x * n, this.y * n);
        },
        divide: function (n) {
          return new e.Point(this.x / n, this.y / n);
        },
        negate: function () {
          return new e.Point(-this.x, -this.y);
        },
        distanceTo: function (n) {
          return Math.sqrt(
            Math.pow(this.x - n.x, 2) + Math.pow(this.y - n.y, 2)
          );
        },
        squaredDistanceTo: function (n) {
          return Math.pow(this.x - n.x, 2) + Math.pow(this.y - n.y, 2);
        },
        apply: function (n) {
          return new e.Point(n(this.x), n(this.y));
        },
        equals: function (n) {
          return n instanceof e.Point && this.x === n.x && this.y === n.y;
        },
        rotate: function (n, r) {
          r = r || new e.Point(0, 0);
          var o;
          var s;
          if (n % 90 == 0) {
            var a = e.positiveModulo(n, 360);
            switch (a) {
              case 0:
                o = 1;
                s = 0;
                break;
              case 90:
                o = 0;
                s = 1;
                break;
              case 180:
                o = -1;
                s = 0;
                break;
              case 270:
                o = 0;
                s = -1;
                break;
            }
          } else {
            var l = (n * Math.PI) / 180;
            o = Math.cos(l);
            s = Math.sin(l);
          }
          var u = o * (this.x - r.x) - s * (this.y - r.y) + r.x;
          var c = s * (this.x - r.x) + o * (this.y - r.y) + r.y;
          return new e.Point(u, c);
        },
        toString: function () {
          return (
            '(' +
            Math.round(this.x * 100) / 100 +
            ',' +
            Math.round(this.y * 100) / 100 +
            ')'
          );
        },
      };
    })();
    (function () {
      function n(r) {
        var o = r.responseText;
        var s = r.status;
        var a;
        var l;
        if (r) {
          if (r.status !== 200 && r.status !== 0) {
            s = r.status;
            a = s === 404 ? 'Not Found' : r.statusText;
            throw new Error(e.getString('Errors.Status', s, a));
          }
        } else {
          throw new Error(e.getString('Errors.Security'));
        }
        if (o.match(/\s*<.*/)) {
          try {
            l =
              r.responseXML && r.responseXML.documentElement
                ? r.responseXML
                : e.parseXml(o);
          } catch {
            l = r.responseText;
          }
        } else if (o.match(/\s*[{[].*/)) {
          try {
            l = e.parseJSON(o);
          } catch {
            l = o;
          }
        } else {
          l = o;
        }
        return l;
      }
      var e = t;
      e.TileSource = function (r, o, s, a, l, u) {
        var c = this;
        var h = arguments;
        var d;
        var g;
        if (e.isPlainObject(r)) {
          d = r;
        } else {
          d = {
            width: h[0],
            height: h[1],
            tileSize: h[2],
            tileOverlap: h[3],
            minLevel: h[4],
            maxLevel: h[5],
          };
        }
        e.EventSource.call(this);
        e.extend(true, this, d);
        if (!this.success) {
          for (g = 0; g < arguments.length; g++) {
            if (e.isFunction(arguments[g])) {
              this.success = arguments[g];
              break;
            }
          }
        }
        if (this.success) {
          this.addHandler('ready', function (y) {
            c.success(y);
          });
        }
        if (e.type(arguments[0]) === 'string') {
          this.url = arguments[0];
        }
        if (this.url) {
          this.aspectRatio = 1;
          this.dimensions = new e.Point(10, 10);
          this._tileWidth = 0;
          this._tileHeight = 0;
          this.tileOverlap = 0;
          this.minLevel = 0;
          this.maxLevel = 0;
          this.ready = false;
          this.getImageInfo(this.url);
        } else {
          this.ready = true;
          this.aspectRatio = d.width && d.height ? d.width / d.height : 1;
          this.dimensions = new e.Point(d.width, d.height);
          if (this.tileSize) {
            this._tileWidth = this._tileHeight = this.tileSize;
            delete this.tileSize;
          } else {
            if (this.tileWidth) {
              this._tileWidth = this.tileWidth;
              delete this.tileWidth;
            } else {
              this._tileWidth = 0;
            }
            if (this.tileHeight) {
              this._tileHeight = this.tileHeight;
              delete this.tileHeight;
            } else {
              this._tileHeight = 0;
            }
          }
          this.tileOverlap = d.tileOverlap ? d.tileOverlap : 0;
          this.minLevel = d.minLevel ? d.minLevel : 0;
          this.maxLevel =
            d.maxLevel !== void 0 && d.maxLevel !== null
              ? d.maxLevel
              : d.width && d.height
              ? Math.ceil(Math.log(Math.max(d.width, d.height)) / Math.log(2))
              : 0;
          if (this.success && e.isFunction(this.success)) {
            this.success(this);
          }
        }
      };
      e.TileSource.prototype = {
        getTileSize: function (r) {
          e.console.error(
            '[TileSource.getTileSize] is deprecated. Use TileSource.getTileWidth() and TileSource.getTileHeight() instead'
          );
          return this._tileWidth;
        },
        getTileWidth: function (r) {
          if (this._tileWidth) {
            return this._tileWidth;
          } else {
            return this.getTileSize(r);
          }
        },
        getTileHeight: function (r) {
          if (this._tileHeight) {
            return this._tileHeight;
          } else {
            return this.getTileSize(r);
          }
        },
        setMaxLevel: function (r) {
          this.maxLevel = r;
          this._memoizeLevelScale();
        },
        getLevelScale: function (r) {
          this._memoizeLevelScale();
          return this.getLevelScale(r);
        },
        _memoizeLevelScale: function () {
          var r = {};
          for (var o = 0; o <= this.maxLevel; o++) {
            r[o] = 1 / Math.pow(2, this.maxLevel - o);
          }
          this.getLevelScale = function (s) {
            return r[s];
          };
        },
        getNumTiles: function (r) {
          var o = this.getLevelScale(r);
          var s = Math.ceil((o * this.dimensions.x) / this.getTileWidth(r));
          var a = Math.ceil((o * this.dimensions.y) / this.getTileHeight(r));
          return new e.Point(s, a);
        },
        getPixelRatio: function (r) {
          var o = this.dimensions.times(this.getLevelScale(r));
          var s = (1 / o.x) * e.pixelDensityRatio;
          var a = (1 / o.y) * e.pixelDensityRatio;
          return new e.Point(s, a);
        },
        getClosestLevel: function () {
          var o;
          for (
            var r = this.minLevel + 1;
            r <= this.maxLevel &&
            ((o = this.getNumTiles(r)), !(o.x > 1) && !(o.y > 1));
            r++
          ) {}
          return r - 1;
        },
        getTileAtPoint: function (r, o) {
          var s =
            o.x >= 0 && o.x <= 1 && o.y >= 0 && o.y <= 1 / this.aspectRatio;
          e.console.assert(
            s,
            '[TileSource.getTileAtPoint] must be called with a valid point.'
          );
          var a = this.dimensions.x * this.getLevelScale(r);
          var l = o.x * a;
          var u = o.y * a;
          var c = Math.floor(l / this.getTileWidth(r));
          var h = Math.floor(u / this.getTileHeight(r));
          if (o.x >= 1) {
            c = this.getNumTiles(r).x - 1;
          }
          var d = 1e-15;
          if (o.y >= 1 / this.aspectRatio - d) {
            h = this.getNumTiles(r).y - 1;
          }
          return new e.Point(c, h);
        },
        getTileBounds: function (r, o, s, a) {
          var l = this.dimensions.times(this.getLevelScale(r));
          var u = this.getTileWidth(r);
          var c = this.getTileHeight(r);
          var h = o === 0 ? 0 : u * o - this.tileOverlap;
          var d = s === 0 ? 0 : c * s - this.tileOverlap;
          var g = u + (o === 0 ? 1 : 2) * this.tileOverlap;
          var y = c + (s === 0 ? 1 : 2) * this.tileOverlap;
          var x = 1 / l.x;
          g = Math.min(g, l.x - h);
          y = Math.min(y, l.y - d);
          if (a) {
            return new e.Rect(0, 0, g, y);
          } else {
            return new e.Rect(h * x, d * x, g * x, y * x);
          }
        },
        getImageInfo: function (r) {
          var o = this;
          var s;
          var l;
          var u;
          var c;
          var h;
          var d;
          if (r) {
            c = r.split('/');
            h = c[c.length - 1];
            d = h.lastIndexOf('.');
            if (d > -1) {
              c[c.length - 1] = h.slice(0, d);
            }
          }
          var a = function (g) {
            if (typeof g == 'string') {
              g = e.parseXml(g);
            }
            var y = e.TileSource.determineType(o, g, r);
            if (!y) {
              o.raiseEvent('open-failed', {
                message: 'Unable to load TileSource',
                source: r,
              });
              return;
            }
            u = y.prototype.configure.apply(o, [g, r]);
            if (u.ajaxWithCredentials === void 0) {
              u.ajaxWithCredentials = o.ajaxWithCredentials;
            }
            l = new y(u);
            o.ready = true;
            o.raiseEvent('ready', { tileSource: l });
          };
          if (r.match(/\.js$/)) {
            s = r.split('/').pop().replace('.js', '');
            e.jsonp({ url: r, async: false, callbackName: s, callback: a });
          } else {
            e.makeAjaxRequest({
              url: r,
              withCredentials: this.ajaxWithCredentials,
              headers: this.ajaxHeaders,
              success: function (g) {
                var y = n(g);
                a(y);
              },
              error: function (g, y) {
                var x;
                try {
                  x = 'HTTP ' + g.status + ' attempting to load TileSource';
                } catch {
                  var b;
                  if (typeof y == 'undefined' || !y.toString) {
                    b = 'Unknown error';
                  } else {
                    b = y.toString();
                  }
                  x = b + ' attempting to load TileSource';
                }
                o.raiseEvent('open-failed', { message: x, source: r });
              },
            });
          }
        },
        supports: function (r, o) {
          return false;
        },
        configure: function (r, o) {
          throw new Error('Method not implemented.');
        },
        getTileUrl: function (r, o, s) {
          throw new Error('Method not implemented.');
        },
        getTileAjaxHeaders: function (r, o, s) {
          return {};
        },
        tileExists: function (r, o, s) {
          var a = this.getNumTiles(r);
          return (
            r >= this.minLevel &&
            r <= this.maxLevel &&
            o >= 0 &&
            s >= 0 &&
            o < a.x &&
            s < a.y
          );
        },
      };
      e.extend(true, e.TileSource.prototype, e.EventSource.prototype);
      e.TileSource.determineType = function (r, o, s) {
        var a;
        for (a in t) {
          if (
            a.match(/.+TileSource$/) &&
            e.isFunction(t[a]) &&
            e.isFunction(t[a].prototype.supports) &&
            t[a].prototype.supports.call(r, o, s)
          ) {
            return t[a];
          }
        }
        e.console.error('No TileSource was able to open %s %s', s, o);
        return null;
      };
    })();
    (function () {
      function n(o, s) {
        if (!s || !s.documentElement) {
          throw new Error(e.getString('Errors.Xml'));
        }
        var a = s.documentElement;
        var l = a.localName || a.tagName;
        var u = s.documentElement.namespaceURI;
        var c = null;
        var h = [];
        var d;
        var g;
        var y;
        var x;
        var b;
        if (l === 'Image') {
          try {
            x = a.getElementsByTagName('Size')[0];
            if (x === void 0) {
              x = a.getElementsByTagNameNS(u, 'Size')[0];
            }
            c = {
              Image: {
                xmlns: 'http://schemas.microsoft.com/deepzoom/2008',
                Url: a.getAttribute('Url'),
                Format: a.getAttribute('Format'),
                DisplayRect: null,
                Overlap: parseInt(a.getAttribute('Overlap'), 10),
                TileSize: parseInt(a.getAttribute('TileSize'), 10),
                Size: {
                  Height: parseInt(x.getAttribute('Height'), 10),
                  Width: parseInt(x.getAttribute('Width'), 10),
                },
              },
            };
            if (!e.imageFormatSupported(c.Image.Format)) {
              throw new Error(
                e.getString('Errors.ImageFormat', c.Image.Format.toUpperCase())
              );
            }
            d = a.getElementsByTagName('DisplayRect');
            if (d === void 0) {
              d = a.getElementsByTagNameNS(u, 'DisplayRect')[0];
            }
            for (b = 0; b < d.length; b++) {
              g = d[b];
              y = g.getElementsByTagName('Rect')[0];
              if (y === void 0) {
                y = g.getElementsByTagNameNS(u, 'Rect')[0];
              }
              h.push({
                Rect: {
                  X: parseInt(y.getAttribute('X'), 10),
                  Y: parseInt(y.getAttribute('Y'), 10),
                  Width: parseInt(y.getAttribute('Width'), 10),
                  Height: parseInt(y.getAttribute('Height'), 10),
                  MinLevel: parseInt(g.getAttribute('MinLevel'), 10),
                  MaxLevel: parseInt(g.getAttribute('MaxLevel'), 10),
                },
              });
            }
            if (h.length) {
              c.Image.DisplayRect = h;
            }
            return r(o, c);
          } catch (E) {
            throw E instanceof Error ? E : new Error(e.getString('Errors.Dzi'));
          }
        } else {
          if (l === 'Collection') {
            throw new Error(e.getString('Errors.Dzc'));
          }
          if (l === 'Error') {
            var T = a.getElementsByTagName('Message')[0];
            var f = T.firstChild.nodeValue;
            throw new Error(f);
          }
        }
        throw new Error(e.getString('Errors.Dzi'));
      }
      function r(o, s) {
        var a = s.Image;
        var l = a.Url;
        var u = a.Format;
        var c = a.Size;
        var h = a.DisplayRect || [];
        var d = parseInt(c.Width, 10);
        var g = parseInt(c.Height, 10);
        var y = parseInt(a.TileSize, 10);
        var x = parseInt(a.Overlap, 10);
        var b = [];
        var T;
        for (var f = 0; f < h.length; f++) {
          T = h[f].Rect;
          b.push(
            new e.DisplayRect(
              parseInt(T.X, 10),
              parseInt(T.Y, 10),
              parseInt(T.Width, 10),
              parseInt(T.Height, 10),
              parseInt(T.MinLevel, 10),
              parseInt(T.MaxLevel, 10)
            )
          );
        }
        return e.extend(
          true,
          {
            width: d,
            height: g,
            tileSize: y,
            tileOverlap: x,
            minLevel: null,
            maxLevel: null,
            tilesUrl: l,
            fileFormat: u,
            displayRects: b,
          },
          s
        );
      }
      var e = t;
      e.DziTileSource = function (o, s, a, l, u, c, h, d, g) {
        var y;
        var x;
        var b;
        var T;
        if (e.isPlainObject(o)) {
          T = o;
        } else {
          T = {
            width: arguments[0],
            height: arguments[1],
            tileSize: arguments[2],
            tileOverlap: arguments[3],
            tilesUrl: arguments[4],
            fileFormat: arguments[5],
            displayRects: arguments[6],
            minLevel: arguments[7],
            maxLevel: arguments[8],
          };
        }
        this._levelRects = {};
        this.tilesUrl = T.tilesUrl;
        this.fileFormat = T.fileFormat;
        this.displayRects = T.displayRects;
        if (this.displayRects) {
          for (y = this.displayRects.length - 1; y >= 0; y--) {
            x = this.displayRects[y];
            for (b = x.minLevel; b <= x.maxLevel; b++) {
              if (!this._levelRects[b]) {
                this._levelRects[b] = [];
              }
              this._levelRects[b].push(x);
            }
          }
        }
        e.TileSource.apply(this, [T]);
      };
      e.extend(e.DziTileSource.prototype, e.TileSource.prototype, {
        supports: function (o, s) {
          if (o.Image) {
            a = o.Image.xmlns;
          } else if (
            o.documentElement &&
            (o.documentElement.localName === 'Image' ||
              o.documentElement.tagName === 'Image')
          ) {
            a = o.documentElement.namespaceURI;
          }
          var a = (a || '').toLowerCase();
          return (
            a.indexOf('schemas.microsoft.com/deepzoom/2008') !== -1 ||
            a.indexOf('schemas.microsoft.com/deepzoom/2009') !== -1
          );
        },
        configure: function (o, s) {
          var a;
          if (e.isPlainObject(o)) {
            a = r(this, o);
          } else {
            a = n(this, o);
          }
          if (s && !a.tilesUrl) {
            a.tilesUrl = s.replace(
              /([^/]+?)(\.(dzi|xml|js)?(\?[^/]*)?)?\/?$/,
              '$1_files/'
            );
            if (s.search(/\.(dzi|xml|js)\?/) === -1) {
              a.queryParams = '';
            } else {
              a.queryParams = s.match(/\?.*/);
            }
          }
          return a;
        },
        getTileUrl: function (o, s, a) {
          return [
            this.tilesUrl,
            o,
            '/',
            s,
            '_',
            a,
            '.',
            this.fileFormat,
            this.queryParams,
          ].join('');
        },
        tileExists: function (o, s, a) {
          var l = this._levelRects[o];
          var u;
          var c;
          var h;
          var d;
          var g;
          var y;
          if (
            (this.minLevel && o < this.minLevel) ||
            (this.maxLevel && o > this.maxLevel)
          ) {
            return false;
          }
          if (!l || !l.length) {
            return true;
          }
          for (var x = l.length - 1; x >= 0; x--) {
            u = l[x];
            if (!(o < u.minLevel) && !(o > u.maxLevel)) {
              c = this.getLevelScale(o);
              h = u.x * c;
              d = u.y * c;
              g = h + u.width * c;
              y = d + u.height * c;
              h = Math.floor(h / this._tileWidth);
              d = Math.floor(d / this._tileWidth);
              g = Math.ceil(g / this._tileWidth);
              y = Math.ceil(y / this._tileWidth);
              if (h <= s && s < g && d <= a && a < y) {
                return true;
              }
            }
          }
          return false;
        },
      });
    })();
    (function () {
      function n(a) {
        var l = [
          'http://library.stanford.edu/iiif/image-api/compliance.html#level0',
          'http://library.stanford.edu/iiif/image-api/1.1/compliance.html#level0',
          'http://iiif.io/api/image/2/level0.json',
          'level0',
          'https://iiif.io/api/image/3/level0.json',
        ];
        var u = Array.isArray(a.profile) ? a.profile[0] : a.profile;
        var c = l.indexOf(u) !== -1;
        var h = false;
        if (a.version === 2 && a.profile.length > 1 && a.profile[1].supports) {
          h = a.profile[1].supports.indexOf('sizeByW') !== -1;
        }
        if (a.version === 3 && a.extraFeatures) {
          h = a.extraFeatures.indexOf('sizeByWh') !== -1;
        }
        return !c || h;
      }
      function r(a) {
        var l = [];
        for (var u = 0; u < a.sizes.length; u++) {
          l.push({
            url:
              a['@id'] +
              '/full/' +
              a.sizes[u].width +
              ',' +
              (a.version === 3 ? a.sizes[u].height : '') +
              '/0/default.' +
              a.tileFormat,
            width: a.sizes[u].width,
            height: a.sizes[u].height,
          });
        }
        return l.sort(function (c, h) {
          return c.width - h.width;
        });
      }
      function o(a) {
        if (!a || !a.documentElement) {
          throw new Error(e.getString('Errors.Xml'));
        }
        var l = a.documentElement;
        var u = l.tagName;
        var c = null;
        if (u === 'info') {
          try {
            c = {};
            s(l, c);
            return c;
          } catch (h) {
            throw h instanceof Error
              ? h
              : new Error(e.getString('Errors.IIIF'));
          }
        }
        throw new Error(e.getString('Errors.IIIF'));
      }
      function s(a, l, u) {
        var c;
        var h;
        if (a.nodeType === 3 && u) {
          h = a.nodeValue.trim();
          if (h.match(/^\d*$/)) {
            h = Number(h);
          }
          if (l[u]) {
            if (!e.isArray(l[u])) {
              l[u] = [l[u]];
            }
            l[u].push(h);
          } else {
            l[u] = h;
          }
        } else if (a.nodeType === 1) {
          for (c = 0; c < a.childNodes.length; c++) {
            s(a.childNodes[c], l, a.nodeName);
          }
        }
      }
      var e = t;
      e.IIIFTileSource = function (a) {
        e.extend(true, this, a);
        if (!this.height || !this.width || !this['@id']) {
          throw new Error('IIIF required parameters not provided.');
        }
        a.tileSizePerScaleFactor = {};
        this.tileFormat = this.tileFormat || 'jpg';
        this.version = a.version;
        if (this.tile_width && this.tile_height) {
          a.tileWidth = this.tile_width;
          a.tileHeight = this.tile_height;
        } else if (this.tile_width) {
          a.tileSize = this.tile_width;
        } else if (this.tile_height) {
          a.tileSize = this.tile_height;
        } else if (this.tiles) {
          if (this.tiles.length === 1) {
            a.tileWidth = this.tiles[0].width;
            a.tileHeight = this.tiles[0].height || this.tiles[0].width;
            this.scale_factors = this.tiles[0].scaleFactors;
          } else {
            this.scale_factors = [];
            for (var l = 0; l < this.tiles.length; l++) {
              for (var u = 0; u < this.tiles[l].scaleFactors.length; u++) {
                var c = this.tiles[l].scaleFactors[u];
                this.scale_factors.push(c);
                a.tileSizePerScaleFactor[c] = {
                  width: this.tiles[l].width,
                  height: this.tiles[l].height || this.tiles[l].width,
                };
              }
            }
          }
        } else if (n(a)) {
          var h = Math.min(this.height, this.width);
          var d = [256, 512, 1024];
          var g = [];
          for (var y = 0; y < d.length; y++) {
            if (d[y] <= h) {
              g.push(d[y]);
            }
          }
          if (g.length > 0) {
            a.tileSize = Math.max.apply(null, g);
          } else {
            a.tileSize = h;
          }
        } else if (this.sizes && this.sizes.length > 0) {
          this.emulateLegacyImagePyramid = true;
          a.levels = r(this);
          e.extend(true, a, {
            width: a.levels[a.levels.length - 1].width,
            height: a.levels[a.levels.length - 1].height,
            tileSize: Math.max(a.height, a.width),
            tileOverlap: 0,
            minLevel: 0,
            maxLevel: a.levels.length - 1,
          });
          this.levels = a.levels;
        } else {
          e.console.error(
            'Nothing in the info.json to construct image pyramids from'
          );
        }
        if (!a.maxLevel && !this.emulateLegacyImagePyramid) {
          if (!this.scale_factors) {
            a.maxLevel = Number(
              Math.ceil(Math.log(Math.max(this.width, this.height), 2))
            );
          } else {
            var x = Math.max.apply(null, this.scale_factors);
            a.maxLevel = Math.round(Math.log(x) * Math.LOG2E);
          }
        }
        e.TileSource.apply(this, [a]);
      };
      e.extend(e.IIIFTileSource.prototype, e.TileSource.prototype, {
        supports: function (a, l) {
          if (
            (a.protocol && a.protocol === 'http://iiif.io/api/image') ||
            (a['@context'] &&
              (a['@context'] ===
                'http://library.stanford.edu/iiif/image-api/1.1/context.json' ||
                a['@context'] === 'http://iiif.io/api/image/1/context.json')) ||
            (a.profile &&
              a.profile.indexOf(
                'http://library.stanford.edu/iiif/image-api/compliance.html'
              ) === 0) ||
            (a.identifier && a.width && a.height)
          ) {
            return true;
          } else {
            return (
              !!a.documentElement &&
              a.documentElement.tagName === 'info' &&
              a.documentElement.namespaceURI ===
                'http://library.stanford.edu/iiif/image-api/ns/'
            );
          }
        },
        configure: function (a, l) {
          if (e.isPlainObject(a)) {
            if (!a['@context']) {
              a['@context'] = 'http://iiif.io/api/image/1.0/context.json';
              a['@id'] = l.replace('/info.json', '');
              a.version = 1;
            } else {
              var c = a['@context'];
              if (Array.isArray(c)) {
                for (var h = 0; h < c.length; h++) {
                  if (
                    typeof c[h] == 'string' &&
                    (/^http:\/\/iiif\.io\/api\/image\/[1-3]\/context\.json$/.test(
                      c[h]
                    ) ||
                      c[h] ===
                        'http://library.stanford.edu/iiif/image-api/1.1/context.json')
                  ) {
                    c = c[h];
                    break;
                  }
                }
              }
              switch (c) {
                case 'http://iiif.io/api/image/1/context.json':
                case 'http://library.stanford.edu/iiif/image-api/1.1/context.json':
                  a.version = 1;
                  break;
                case 'http://iiif.io/api/image/2/context.json':
                  a.version = 2;
                  break;
                case 'http://iiif.io/api/image/3/context.json':
                  a.version = 3;
                  break;
                default:
                  e.console.error(
                    'Data has a @context property which contains no known IIIF context URI.'
                  );
              }
            }
            if (!a['@id'] && a.id) {
              a['@id'] = a.id;
            }
            if (a.preferredFormats) {
              for (var d = 0; d < a.preferredFormats.length; d++) {
                if (t.imageFormatSupported(a.preferredFormats[d])) {
                  a.tileFormat = a.preferredFormats[d];
                  break;
                }
              }
            }
            return a;
          } else {
            var u = o(a);
            u['@context'] = 'http://iiif.io/api/image/1.0/context.json';
            u['@id'] = l.replace('/info.xml', '');
            u.version = 1;
            return u;
          }
        },
        getTileWidth: function (a) {
          if (this.emulateLegacyImagePyramid) {
            return e.TileSource.prototype.getTileWidth.call(this, a);
          }
          var l = Math.pow(2, this.maxLevel - a);
          if (this.tileSizePerScaleFactor && this.tileSizePerScaleFactor[l]) {
            return this.tileSizePerScaleFactor[l].width;
          } else {
            return this._tileWidth;
          }
        },
        getTileHeight: function (a) {
          if (this.emulateLegacyImagePyramid) {
            return e.TileSource.prototype.getTileHeight.call(this, a);
          }
          var l = Math.pow(2, this.maxLevel - a);
          if (this.tileSizePerScaleFactor && this.tileSizePerScaleFactor[l]) {
            return this.tileSizePerScaleFactor[l].height;
          } else {
            return this._tileHeight;
          }
        },
        getLevelScale: function (a) {
          if (this.emulateLegacyImagePyramid) {
            var l = NaN;
            if (
              this.levels.length > 0 &&
              a >= this.minLevel &&
              a <= this.maxLevel
            ) {
              l = this.levels[a].width / this.levels[this.maxLevel].width;
            }
            return l;
          }
          return e.TileSource.prototype.getLevelScale.call(this, a);
        },
        getNumTiles: function (a) {
          if (this.emulateLegacyImagePyramid) {
            var l = this.getLevelScale(a);
            if (l) {
              return new e.Point(1, 1);
            } else {
              return new e.Point(0, 0);
            }
          }
          return e.TileSource.prototype.getNumTiles.call(this, a);
        },
        getTileAtPoint: function (a, l) {
          if (this.emulateLegacyImagePyramid) {
            return new e.Point(0, 0);
          } else {
            return e.TileSource.prototype.getTileAtPoint.call(this, a, l);
          }
        },
        getTileUrl: function (a, l, u) {
          if (this.emulateLegacyImagePyramid) {
            var c = null;
            if (
              this.levels.length > 0 &&
              a >= this.minLevel &&
              a <= this.maxLevel
            ) {
              c = this.levels[a].url;
            }
            return c;
          }
          var h = '0';
          var d = Math.pow(0.5, this.maxLevel - a);
          var g = Math.ceil(this.width * d);
          var y = Math.ceil(this.height * d);
          var E;
          var A;
          var C;
          var O;
          var D;
          var N;
          var B;
          var Z;
          var Y;
          var x = this.getTileWidth(a);
          var b = this.getTileHeight(a);
          var T = Math.ceil(x / d);
          var f = Math.ceil(b / d);
          if (this.version === 1) {
            Y = 'native.' + this.tileFormat;
          } else {
            Y = 'default.' + this.tileFormat;
          }
          if (g < x && y < b) {
            if (this.version === 2 && g === this.width) {
              N = 'full';
            } else if (
              this.version === 3 &&
              g === this.width &&
              y === this.height
            ) {
              N = 'max';
            } else if (this.version === 3) {
              N = g + ',' + y;
            } else {
              N = g + ',';
            }
            E = 'full';
          } else {
            A = l * T;
            C = u * f;
            O = Math.min(T, this.width - A);
            D = Math.min(f, this.height - C);
            if (l === 0 && u === 0 && O === this.width && D === this.height) {
              E = 'full';
            } else {
              E = [A, C, O, D].join(',');
            }
            B = Math.ceil(O * d);
            Z = Math.ceil(D * d);
            if (this.version === 2 && B === this.width) {
              N = 'full';
            } else if (
              this.version === 3 &&
              B === this.width &&
              Z === this.height
            ) {
              N = 'max';
            } else if (this.version === 3) {
              N = B + ',' + Z;
            } else {
              N = B + ',';
            }
          }
          var U = [this['@id'], E, N, h, Y].join('/');
          return U;
        },
        __testonly__: { canBeTiled: n, constructLevels: r },
      });
    })();
    (function () {
      var e = t;
      e.OsmTileSource = function (n, r, o, s, a) {
        var l;
        if (e.isPlainObject(n)) {
          l = n;
        } else {
          l = {
            width: arguments[0],
            height: arguments[1],
            tileSize: arguments[2],
            tileOverlap: arguments[3],
            tilesUrl: arguments[4],
          };
        }
        if (!l.width || !l.height) {
          l.width = 65572864;
          l.height = 65572864;
        }
        if (!l.tileSize) {
          l.tileSize = 256;
          l.tileOverlap = 0;
        }
        if (!l.tilesUrl) {
          l.tilesUrl = 'http://tile.openstreetmap.org/';
        }
        l.minLevel = 8;
        e.TileSource.apply(this, [l]);
      };
      e.extend(e.OsmTileSource.prototype, e.TileSource.prototype, {
        supports: function (n, r) {
          return n.type && n.type === 'openstreetmaps';
        },
        configure: function (n, r) {
          return n;
        },
        getTileUrl: function (n, r, o) {
          return this.tilesUrl + (n - 8) + '/' + r + '/' + o + '.png';
        },
      });
    })();
    (function () {
      var e = t;
      e.TmsTileSource = function (n, r, o, s, a) {
        var l;
        if (e.isPlainObject(n)) {
          l = n;
        } else {
          l = {
            width: arguments[0],
            height: arguments[1],
            tileSize: arguments[2],
            tileOverlap: arguments[3],
            tilesUrl: arguments[4],
          };
        }
        var u = Math.ceil(l.width / 256) * 256;
        var c = Math.ceil(l.height / 256) * 256;
        var h;
        if (u > c) {
          h = u / 256;
        } else {
          h = c / 256;
        }
        l.maxLevel = Math.ceil(Math.log(h) / Math.log(2)) - 1;
        l.tileSize = 256;
        l.width = u;
        l.height = c;
        e.TileSource.apply(this, [l]);
      };
      e.extend(e.TmsTileSource.prototype, e.TileSource.prototype, {
        supports: function (n, r) {
          return n.type && n.type === 'tiledmapservice';
        },
        configure: function (n, r) {
          return n;
        },
        getTileUrl: function (n, r, o) {
          var s = this.getNumTiles(n).y - 1;
          return this.tilesUrl + n + '/' + r + '/' + (s - o) + '.png';
        },
      });
    })();
    (function () {
      var e = t;
      e.ZoomifyTileSource = function (n) {
        if (typeof n.tileSize == 'undefined') {
          n.tileSize = 256;
        }
        if (typeof n.fileFormat == 'undefined') {
          n.fileFormat = 'jpg';
          this.fileFormat = n.fileFormat;
        }
        var r = { x: n.width, y: n.height };
        n.imageSizes = [{ x: n.width, y: n.height }];
        for (
          n.gridSize = [this._getGridSize(n.width, n.height, n.tileSize)];
          parseInt(r.x, 10) > n.tileSize || parseInt(r.y, 10) > n.tileSize;

        ) {
          r.x = Math.floor(r.x / 2);
          r.y = Math.floor(r.y / 2);
          n.imageSizes.push({ x: r.x, y: r.y });
          n.gridSize.push(this._getGridSize(r.x, r.y, n.tileSize));
        }
        n.imageSizes.reverse();
        n.gridSize.reverse();
        n.minLevel = 0;
        n.maxLevel = n.gridSize.length - 1;
        t.TileSource.apply(this, [n]);
      };
      e.extend(e.ZoomifyTileSource.prototype, e.TileSource.prototype, {
        _getGridSize: function (n, r, o) {
          return { x: Math.ceil(n / o), y: Math.ceil(r / o) };
        },
        _calculateAbsoluteTileNumber: function (n, r, o) {
          var s = 0;
          var a = {};
          for (var l = 0; l < n; l++) {
            a = this.gridSize[l];
            s += a.x * a.y;
          }
          a = this.gridSize[n];
          s += a.x * o + r;
          return s;
        },
        supports: function (n, r) {
          return n.type && n.type === 'zoomifytileservice';
        },
        configure: function (n, r) {
          return n;
        },
        getTileUrl: function (n, r, o) {
          var s = 0;
          var a = this._calculateAbsoluteTileNumber(n, r, o);
          s = Math.floor(a / 256);
          return (
            this.tilesUrl +
            'TileGroup' +
            s +
            '/' +
            n +
            '-' +
            r +
            '-' +
            o +
            '.' +
            this.fileFormat
          );
        },
      });
    })();
    (function () {
      function n(s) {
        var a = [];
        var l;
        for (var u = 0; u < s.length; u++) {
          l = s[u];
          if (l.height && l.width && l.url) {
            a.push({
              url: l.url,
              width: Number(l.width),
              height: Number(l.height),
            });
          } else {
            e.console.error(
              'Unsupported image format: %s',
              l.url ? l.url : '<no URL>'
            );
          }
        }
        return a.sort(function (c, h) {
          return c.height - h.height;
        });
      }
      function r(s, a) {
        if (!a || !a.documentElement) {
          throw new Error(e.getString('Errors.Xml'));
        }
        var l = a.documentElement;
        var u = l.tagName;
        var c = null;
        var h = [];
        var d;
        var g;
        if (u === 'image') {
          try {
            c = { type: l.getAttribute('type'), levels: [] };
            h = l.getElementsByTagName('level');
            for (g = 0; g < h.length; g++) {
              d = h[g];
              c.levels.push({
                url: d.getAttribute('url'),
                width: parseInt(d.getAttribute('width'), 10),
                height: parseInt(d.getAttribute('height'), 10),
              });
            }
            return o(s, c);
          } catch (y) {
            throw y instanceof Error
              ? y
              : new Error('Unknown error parsing Legacy Image Pyramid XML.');
          }
        } else {
          if (u === 'collection') {
            throw new Error(
              'Legacy Image Pyramid Collections not yet supported.'
            );
          }
          if (u === 'error') {
            throw new Error('Error: ' + a);
          }
        }
        throw new Error('Unknown element ' + u);
      }
      function o(s, a) {
        return a.levels;
      }
      var e = t;
      e.LegacyTileSource = function (s) {
        var a;
        var l;
        var u;
        if (e.isArray(s)) {
          a = { type: 'legacy-image-pyramid', levels: s };
        }
        a.levels = n(a.levels);
        if (a.levels.length > 0) {
          l = a.levels[a.levels.length - 1].width;
          u = a.levels[a.levels.length - 1].height;
        } else {
          l = 0;
          u = 0;
          e.console.error('No supported image formats found');
        }
        e.extend(true, a, {
          width: l,
          height: u,
          tileSize: Math.max(u, l),
          tileOverlap: 0,
          minLevel: 0,
          maxLevel: a.levels.length > 0 ? a.levels.length - 1 : 0,
        });
        e.TileSource.apply(this, [a]);
        this.levels = a.levels;
      };
      e.extend(e.LegacyTileSource.prototype, e.TileSource.prototype, {
        supports: function (s, a) {
          return (
            (s.type && s.type === 'legacy-image-pyramid') ||
            (s.documentElement &&
              s.documentElement.getAttribute('type') === 'legacy-image-pyramid')
          );
        },
        configure: function (s, a) {
          var l;
          if (e.isPlainObject(s)) {
            l = o(this, s);
          } else {
            l = r(this, s);
          }
          return l;
        },
        getLevelScale: function (s) {
          var a = NaN;
          if (
            this.levels.length > 0 &&
            s >= this.minLevel &&
            s <= this.maxLevel
          ) {
            a = this.levels[s].width / this.levels[this.maxLevel].width;
          }
          return a;
        },
        getNumTiles: function (s) {
          var a = this.getLevelScale(s);
          if (a) {
            return new e.Point(1, 1);
          } else {
            return new e.Point(0, 0);
          }
        },
        getTileUrl: function (s, a, l) {
          var u = null;
          if (
            this.levels.length > 0 &&
            s >= this.minLevel &&
            s <= this.maxLevel
          ) {
            u = this.levels[s].url;
          }
          return u;
        },
      });
    })();
    (function () {
      var e = t;
      e.ImageTileSource = function (n) {
        n = e.extend(
          {
            buildPyramid: true,
            crossOriginPolicy: false,
            ajaxWithCredentials: false,
            useCanvas: true,
          },
          n
        );
        e.TileSource.apply(this, [n]);
      };
      e.extend(e.ImageTileSource.prototype, e.TileSource.prototype, {
        supports: function (n, r) {
          return n.type && n.type === 'image';
        },
        configure: function (n, r) {
          return n;
        },
        getImageInfo: function (n) {
          var r = (this._image = new Image());
          var o = this;
          if (this.crossOriginPolicy) {
            r.crossOrigin = this.crossOriginPolicy;
          }
          if (this.ajaxWithCredentials) {
            r.useCredentials = this.ajaxWithCredentials;
          }
          e.addEvent(r, 'load', function () {
            o.width = r.naturalWidth;
            o.height = r.naturalHeight;
            o.aspectRatio = o.width / o.height;
            o.dimensions = new e.Point(o.width, o.height);
            o._tileWidth = o.width;
            o._tileHeight = o.height;
            o.tileOverlap = 0;
            o.minLevel = 0;
            o.levels = o._buildLevels();
            o.maxLevel = o.levels.length - 1;
            o.ready = true;
            o.raiseEvent('ready', { tileSource: o });
          });
          e.addEvent(r, 'error', function () {
            o.raiseEvent('open-failed', {
              message: 'Error loading image at ' + n,
              source: n,
            });
          });
          r.src = n;
        },
        getLevelScale: function (n) {
          var r = NaN;
          if (n >= this.minLevel && n <= this.maxLevel) {
            r = this.levels[n].width / this.levels[this.maxLevel].width;
          }
          return r;
        },
        getNumTiles: function (n) {
          var r = this.getLevelScale(n);
          if (r) {
            return new e.Point(1, 1);
          } else {
            return new e.Point(0, 0);
          }
        },
        getTileUrl: function (n, r, o) {
          var s = null;
          if (n >= this.minLevel && n <= this.maxLevel) {
            s = this.levels[n].url;
          }
          return s;
        },
        getContext2D: function (n, r, o) {
          var s = null;
          if (n >= this.minLevel && n <= this.maxLevel) {
            s = this.levels[n].context2D;
          }
          return s;
        },
        destroy: function () {
          this._freeupCanvasMemory();
        },
        _buildLevels: function () {
          var n = [
            {
              url: this._image.src,
              width: this._image.naturalWidth,
              height: this._image.naturalHeight,
            },
          ];
          if (!this.buildPyramid || !e.supportsCanvas || !this.useCanvas) {
            delete this._image;
            return n;
          }
          var r = this._image.naturalWidth;
          var o = this._image.naturalHeight;
          var s = document.createElement('canvas');
          var a = s.getContext('2d');
          s.width = r;
          s.height = o;
          a.drawImage(this._image, 0, 0, r, o);
          n[0].context2D = a;
          delete this._image;
          if (e.isCanvasTainted(s)) {
            return n;
          }
          while (r >= 2 && o >= 2) {
            r = Math.floor(r / 2);
            o = Math.floor(o / 2);
            var l = document.createElement('canvas');
            var u = l.getContext('2d');
            l.width = r;
            l.height = o;
            u.drawImage(s, 0, 0, r, o);
            n.splice(0, 0, { context2D: u, width: r, height: o });
            s = l;
            a = u;
          }
          return n;
        },
        _freeupCanvasMemory: function () {
          for (var n = 0; n < this.levels.length; n++) {
            this.levels[n].context2D.canvas.height = 0;
            this.levels[n].context2D.canvas.width = 0;
          }
        },
      });
    })();
    (function () {
      var e = t;
      e.TileSourceCollection = function (n, r, o, s) {
        e.console.error(
          'TileSourceCollection is deprecated; use World instead'
        );
      };
    })();
    (function () {
      function n(u) {
        e.requestAnimationFrame(function () {
          r(u);
        });
      }
      function r(u) {
        var c;
        var h;
        var d;
        if (u.shouldFade) {
          c = e.now();
          h = c - u.fadeBeginTime;
          d = 1 - h / u.fadeLength;
          d = Math.min(1, d);
          d = Math.max(0, d);
          if (u.imgGroup) {
            e.setElementOpacity(u.imgGroup, d, true);
          }
          if (d > 0) {
            n(u);
          }
        }
      }
      function o(u) {
        u.shouldFade = true;
        u.fadeBeginTime = e.now() + u.fadeDelay;
        window.setTimeout(function () {
          n(u);
        }, u.fadeDelay);
      }
      function s(u) {
        u.shouldFade = false;
        if (u.imgGroup) {
          e.setElementOpacity(u.imgGroup, 1, true);
        }
      }
      function a(u, c) {
        if (!u.element.disabled) {
          if (
            c >= e.ButtonState.GROUP &&
            u.currentState === e.ButtonState.REST
          ) {
            s(u);
            u.currentState = e.ButtonState.GROUP;
          }
          if (
            c >= e.ButtonState.HOVER &&
            u.currentState === e.ButtonState.GROUP
          ) {
            if (u.imgHover) {
              u.imgHover.style.visibility = '';
            }
            u.currentState = e.ButtonState.HOVER;
          }
          if (
            c >= e.ButtonState.DOWN &&
            u.currentState === e.ButtonState.HOVER
          ) {
            if (u.imgDown) {
              u.imgDown.style.visibility = '';
            }
            u.currentState = e.ButtonState.DOWN;
          }
        }
      }
      function l(u, c) {
        if (!u.element.disabled) {
          if (
            c <= e.ButtonState.HOVER &&
            u.currentState === e.ButtonState.DOWN
          ) {
            if (u.imgDown) {
              u.imgDown.style.visibility = 'hidden';
            }
            u.currentState = e.ButtonState.HOVER;
          }
          if (
            c <= e.ButtonState.GROUP &&
            u.currentState === e.ButtonState.HOVER
          ) {
            if (u.imgHover) {
              u.imgHover.style.visibility = 'hidden';
            }
            u.currentState = e.ButtonState.GROUP;
          }
          if (
            c <= e.ButtonState.REST &&
            u.currentState === e.ButtonState.GROUP
          ) {
            o(u);
            u.currentState = e.ButtonState.REST;
          }
        }
      }
      var e = t;
      e.ButtonState = { REST: 0, GROUP: 1, HOVER: 2, DOWN: 3 };
      e.Button = function (u) {
        var c = this;
        e.EventSource.call(this);
        e.extend(
          true,
          this,
          {
            tooltip: null,
            srcRest: null,
            srcGroup: null,
            srcHover: null,
            srcDown: null,
            clickTimeThreshold: e.DEFAULT_SETTINGS.clickTimeThreshold,
            clickDistThreshold: e.DEFAULT_SETTINGS.clickDistThreshold,
            fadeDelay: 0,
            fadeLength: 2e3,
            onPress: null,
            onRelease: null,
            onClick: null,
            onEnter: null,
            onExit: null,
            onFocus: null,
            onBlur: null,
            userData: null,
          },
          u
        );
        this.element = u.element || e.makeNeutralElement('div');
        if (!u.element) {
          this.imgRest = e.makeTransparentImage(this.srcRest);
          this.imgGroup = e.makeTransparentImage(this.srcGroup);
          this.imgHover = e.makeTransparentImage(this.srcHover);
          this.imgDown = e.makeTransparentImage(this.srcDown);
          this.imgRest.alt =
            this.imgGroup.alt =
            this.imgHover.alt =
            this.imgDown.alt =
              this.tooltip;
          e.setElementPointerEventsNone(this.imgRest);
          e.setElementPointerEventsNone(this.imgGroup);
          e.setElementPointerEventsNone(this.imgHover);
          e.setElementPointerEventsNone(this.imgDown);
          this.element.style.position = 'relative';
          e.setElementTouchActionNone(this.element);
          this.imgGroup.style.position =
            this.imgHover.style.position =
            this.imgDown.style.position =
              'absolute';
          this.imgGroup.style.top =
            this.imgHover.style.top =
            this.imgDown.style.top =
              '0px';
          this.imgGroup.style.left =
            this.imgHover.style.left =
            this.imgDown.style.left =
              '0px';
          this.imgHover.style.visibility = this.imgDown.style.visibility =
            'hidden';
          if (
            e.Browser.vendor === e.BROWSERS.FIREFOX &&
            e.Browser.version < 3
          ) {
            this.imgGroup.style.top =
              this.imgHover.style.top =
              this.imgDown.style.top =
                '';
          }
          this.element.appendChild(this.imgRest);
          this.element.appendChild(this.imgGroup);
          this.element.appendChild(this.imgHover);
          this.element.appendChild(this.imgDown);
        }
        this.addHandler('press', this.onPress);
        this.addHandler('release', this.onRelease);
        this.addHandler('click', this.onClick);
        this.addHandler('enter', this.onEnter);
        this.addHandler('exit', this.onExit);
        this.addHandler('focus', this.onFocus);
        this.addHandler('blur', this.onBlur);
        this.currentState = e.ButtonState.GROUP;
        this.fadeBeginTime = null;
        this.shouldFade = false;
        this.element.style.display = 'inline-block';
        this.element.style.position = 'relative';
        this.element.title = this.tooltip;
        this.tracker = new e.MouseTracker({
          userData: 'Button.tracker',
          element: this.element,
          clickTimeThreshold: this.clickTimeThreshold,
          clickDistThreshold: this.clickDistThreshold,
          enterHandler: function (h) {
            if (h.insideElementPressed) {
              a(c, e.ButtonState.DOWN);
              c.raiseEvent('enter', { originalEvent: h.originalEvent });
            } else if (!h.buttonDownAny) {
              a(c, e.ButtonState.HOVER);
            }
          },
          focusHandler: function (h) {
            c.tracker.enterHandler(h);
            c.raiseEvent('focus', { originalEvent: h.originalEvent });
          },
          leaveHandler: function (h) {
            l(c, e.ButtonState.GROUP);
            if (h.insideElementPressed) {
              c.raiseEvent('exit', { originalEvent: h.originalEvent });
            }
          },
          blurHandler: function (h) {
            c.tracker.leaveHandler(h);
            c.raiseEvent('blur', { originalEvent: h.originalEvent });
          },
          pressHandler: function (h) {
            a(c, e.ButtonState.DOWN);
            c.raiseEvent('press', { originalEvent: h.originalEvent });
          },
          releaseHandler: function (h) {
            if (h.insideElementPressed && h.insideElementReleased) {
              l(c, e.ButtonState.HOVER);
              c.raiseEvent('release', { originalEvent: h.originalEvent });
            } else if (h.insideElementPressed) {
              l(c, e.ButtonState.GROUP);
            } else {
              a(c, e.ButtonState.HOVER);
            }
          },
          clickHandler: function (h) {
            if (h.quick) {
              c.raiseEvent('click', { originalEvent: h.originalEvent });
            }
          },
          keyHandler: function (h) {
            if (h.keyCode === 13) {
              c.raiseEvent('click', { originalEvent: h.originalEvent });
              c.raiseEvent('release', { originalEvent: h.originalEvent });
              h.preventDefault = true;
            } else {
              h.preventDefault = false;
            }
          },
        });
        l(this, e.ButtonState.REST);
      };
      e.extend(e.Button.prototype, e.EventSource.prototype, {
        notifyGroupEnter: function () {
          a(this, e.ButtonState.GROUP);
        },
        notifyGroupExit: function () {
          l(this, e.ButtonState.REST);
        },
        disable: function () {
          this.notifyGroupExit();
          this.element.disabled = true;
          e.setElementOpacity(this.element, 0.2, true);
        },
        enable: function () {
          this.element.disabled = false;
          e.setElementOpacity(this.element, 1, true);
          this.notifyGroupEnter();
        },
        destroy: function () {
          if (this.imgRest) {
            this.element.removeChild(this.imgRest);
            this.imgRest = null;
          }
          if (this.imgGroup) {
            this.element.removeChild(this.imgGroup);
            this.imgGroup = null;
          }
          if (this.imgHover) {
            this.element.removeChild(this.imgHover);
            this.imgHover = null;
          }
          if (this.imgDown) {
            this.element.removeChild(this.imgDown);
            this.imgDown = null;
          }
          this.removeAllHandlers();
          this.tracker.destroy();
          this.element = null;
        },
      });
    })();
    (function () {
      var e = t;
      e.ButtonGroup = function (n) {
        e.extend(
          true,
          this,
          {
            buttons: [],
            clickTimeThreshold: e.DEFAULT_SETTINGS.clickTimeThreshold,
            clickDistThreshold: e.DEFAULT_SETTINGS.clickDistThreshold,
            labelText: '',
          },
          n
        );
        var r = this.buttons.concat([]);
        var o = this;
        var s;
        this.element = n.element || e.makeNeutralElement('div');
        if (!n.group) {
          this.element.style.display = 'inline-block';
          for (s = 0; s < r.length; s++) {
            this.element.appendChild(r[s].element);
          }
        }
        e.setElementTouchActionNone(this.element);
        this.tracker = new e.MouseTracker({
          userData: 'ButtonGroup.tracker',
          element: this.element,
          clickTimeThreshold: this.clickTimeThreshold,
          clickDistThreshold: this.clickDistThreshold,
          enterHandler: function (a) {
            for (var l = 0; l < o.buttons.length; l++) {
              o.buttons[l].notifyGroupEnter();
            }
          },
          leaveHandler: function (a) {
            var l;
            if (!a.insideElementPressed) {
              for (l = 0; l < o.buttons.length; l++) {
                o.buttons[l].notifyGroupExit();
              }
            }
          },
        });
      };
      e.ButtonGroup.prototype = {
        emulateEnter: function () {
          this.tracker.enterHandler({ eventSource: this.tracker });
        },
        emulateLeave: function () {
          this.tracker.leaveHandler({ eventSource: this.tracker });
        },
        destroy: function () {
          while (this.buttons.length) {
            var n = this.buttons.pop();
            this.element.removeChild(n.element);
            n.destroy();
          }
          this.tracker.destroy();
          this.element = null;
        },
      };
    })();
    (function () {
      var e = t;
      e.Rect = function (n, r, o, s, a) {
        this.x = typeof n == 'number' ? n : 0;
        this.y = typeof r == 'number' ? r : 0;
        this.width = typeof o == 'number' ? o : 0;
        this.height = typeof s == 'number' ? s : 0;
        this.degrees = typeof a == 'number' ? a : 0;
        this.degrees = e.positiveModulo(this.degrees, 360);
        var l;
        var u;
        if (this.degrees >= 270) {
          l = this.getTopRight();
          this.x = l.x;
          this.y = l.y;
          u = this.height;
          this.height = this.width;
          this.width = u;
          this.degrees -= 270;
        } else if (this.degrees >= 180) {
          l = this.getBottomRight();
          this.x = l.x;
          this.y = l.y;
          this.degrees -= 180;
        } else if (this.degrees >= 90) {
          l = this.getBottomLeft();
          this.x = l.x;
          this.y = l.y;
          u = this.height;
          this.height = this.width;
          this.width = u;
          this.degrees -= 90;
        }
      };
      e.Rect.fromSummits = function (n, r, o) {
        var s = n.distanceTo(r);
        var a = n.distanceTo(o);
        var l = r.minus(n);
        var u = Math.atan(l.y / l.x);
        if (l.x < 0) {
          u += Math.PI;
        } else if (l.y < 0) {
          u += 2 * Math.PI;
        }
        return new e.Rect(n.x, n.y, s, a, (u / Math.PI) * 180);
      };
      e.Rect.prototype = {
        clone: function () {
          return new e.Rect(
            this.x,
            this.y,
            this.width,
            this.height,
            this.degrees
          );
        },
        getAspectRatio: function () {
          return this.width / this.height;
        },
        getTopLeft: function () {
          return new e.Point(this.x, this.y);
        },
        getBottomRight: function () {
          return new e.Point(this.x + this.width, this.y + this.height).rotate(
            this.degrees,
            this.getTopLeft()
          );
        },
        getTopRight: function () {
          return new e.Point(this.x + this.width, this.y).rotate(
            this.degrees,
            this.getTopLeft()
          );
        },
        getBottomLeft: function () {
          return new e.Point(this.x, this.y + this.height).rotate(
            this.degrees,
            this.getTopLeft()
          );
        },
        getCenter: function () {
          return new e.Point(
            this.x + this.width / 2,
            this.y + this.height / 2
          ).rotate(this.degrees, this.getTopLeft());
        },
        getSize: function () {
          return new e.Point(this.width, this.height);
        },
        equals: function (n) {
          return (
            n instanceof e.Rect &&
            this.x === n.x &&
            this.y === n.y &&
            this.width === n.width &&
            this.height === n.height &&
            this.degrees === n.degrees
          );
        },
        times: function (n) {
          return new e.Rect(
            this.x * n,
            this.y * n,
            this.width * n,
            this.height * n,
            this.degrees
          );
        },
        translate: function (n) {
          return new e.Rect(
            this.x + n.x,
            this.y + n.y,
            this.width,
            this.height,
            this.degrees
          );
        },
        union: function (n) {
          var r = this.getBoundingBox();
          var o = n.getBoundingBox();
          var s = Math.min(r.x, o.x);
          var a = Math.min(r.y, o.y);
          var l = Math.max(r.x + r.width, o.x + o.width);
          var u = Math.max(r.y + r.height, o.y + o.height);
          return new e.Rect(s, a, l - s, u - a);
        },
        intersection: function (n) {
          function C(U, K, Q, le) {
            var re = K.minus(U);
            var se = le.minus(Q);
            var fe = -se.x * re.y + re.x * se.y;
            if (fe === 0) {
              return null;
            }
            var me = (re.x * (U.y - Q.y) - re.y * (U.x - Q.x)) / fe;
            var q = (se.x * (U.y - Q.y) - se.y * (U.x - Q.x)) / fe;
            if (-r <= me && me <= 1 - r && -r <= q && q <= 1 - r) {
              return new e.Point(U.x + q * re.x, U.y + q * re.y);
            } else {
              return null;
            }
          }
          var r = 1e-10;
          var o = [];
          var s = this.getTopLeft();
          if (n.containsPoint(s, r)) {
            o.push(s);
          }
          var a = this.getTopRight();
          if (n.containsPoint(a, r)) {
            o.push(a);
          }
          var l = this.getBottomLeft();
          if (n.containsPoint(l, r)) {
            o.push(l);
          }
          var u = this.getBottomRight();
          if (n.containsPoint(u, r)) {
            o.push(u);
          }
          var c = n.getTopLeft();
          if (this.containsPoint(c, r)) {
            o.push(c);
          }
          var h = n.getTopRight();
          if (this.containsPoint(h, r)) {
            o.push(h);
          }
          var d = n.getBottomLeft();
          if (this.containsPoint(d, r)) {
            o.push(d);
          }
          var g = n.getBottomRight();
          if (this.containsPoint(g, r)) {
            o.push(g);
          }
          var y = this._getSegments();
          var x = n._getSegments();
          for (var b = 0; b < y.length; b++) {
            var T = y[b];
            for (var f = 0; f < x.length; f++) {
              var E = x[f];
              var A = C(T[0], T[1], E[0], E[1]);
              if (A) {
                o.push(A);
              }
            }
          }
          if (o.length === 0) {
            return null;
          }
          var O = o[0].x;
          var D = o[0].x;
          var N = o[0].y;
          var B = o[0].y;
          for (var Z = 1; Z < o.length; Z++) {
            var Y = o[Z];
            if (Y.x < O) {
              O = Y.x;
            }
            if (Y.x > D) {
              D = Y.x;
            }
            if (Y.y < N) {
              N = Y.y;
            }
            if (Y.y > B) {
              B = Y.y;
            }
          }
          return new e.Rect(O, N, D - O, B - N);
        },
        _getSegments: function () {
          var n = this.getTopLeft();
          var r = this.getTopRight();
          var o = this.getBottomLeft();
          var s = this.getBottomRight();
          return [
            [n, r],
            [r, s],
            [s, o],
            [o, n],
          ];
        },
        rotate: function (n, r) {
          n = e.positiveModulo(n, 360);
          if (n === 0) {
            return this.clone();
          }
          r = r || this.getCenter();
          var o = this.getTopLeft().rotate(n, r);
          var s = this.getTopRight().rotate(n, r);
          var a = s.minus(o);
          a = a.apply(function (u) {
            var c = 1e-15;
            if (Math.abs(u) < c) {
              return 0;
            } else {
              return u;
            }
          });
          var l = Math.atan(a.y / a.x);
          if (a.x < 0) {
            l += Math.PI;
          } else if (a.y < 0) {
            l += 2 * Math.PI;
          }
          return new e.Rect(
            o.x,
            o.y,
            this.width,
            this.height,
            (l / Math.PI) * 180
          );
        },
        getBoundingBox: function () {
          if (this.degrees === 0) {
            return this.clone();
          }
          var n = this.getTopLeft();
          var r = this.getTopRight();
          var o = this.getBottomLeft();
          var s = this.getBottomRight();
          var a = Math.min(n.x, r.x, o.x, s.x);
          var l = Math.max(n.x, r.x, o.x, s.x);
          var u = Math.min(n.y, r.y, o.y, s.y);
          var c = Math.max(n.y, r.y, o.y, s.y);
          return new e.Rect(a, u, l - a, c - u);
        },
        getIntegerBoundingBox: function () {
          var n = this.getBoundingBox();
          var r = Math.floor(n.x);
          var o = Math.floor(n.y);
          var s = Math.ceil(n.width + n.x - r);
          var a = Math.ceil(n.height + n.y - o);
          return new e.Rect(r, o, s, a);
        },
        containsPoint: function (n, r) {
          r = r || 0;
          var o = this.getTopLeft();
          var s = this.getTopRight();
          var a = this.getBottomLeft();
          var l = s.minus(o);
          var u = a.minus(o);
          return (
            (n.x - o.x) * l.x + (n.y - o.y) * l.y >= -r &&
            (n.x - s.x) * l.x + (n.y - s.y) * l.y <= r &&
            (n.x - o.x) * u.x + (n.y - o.y) * u.y >= -r &&
            (n.x - a.x) * u.x + (n.y - a.y) * u.y <= r
          );
        },
        toString: function () {
          return (
            '[' +
            Math.round(this.x * 100) / 100 +
            ', ' +
            Math.round(this.y * 100) / 100 +
            ', ' +
            Math.round(this.width * 100) / 100 +
            'x' +
            Math.round(this.height * 100) / 100 +
            ', ' +
            Math.round(this.degrees * 100) / 100 +
            'deg]'
          );
        },
      };
    })();
    (function () {
      function r(d) {
        if (d.quick) {
          var g;
          if (this.scroll === 'horizontal') {
            g = Math.floor(d.position.x / this.panelWidth);
          } else {
            g = Math.floor(d.position.y / this.panelHeight);
          }
          this.viewer.goToPage(g);
        }
        this.element.focus();
      }
      function o(d) {
        this.dragging = true;
        if (this.element) {
          var g = Number(this.element.style.marginLeft.replace('px', ''));
          var y = Number(this.element.style.marginTop.replace('px', ''));
          var x = Number(this.element.style.width.replace('px', ''));
          var b = Number(this.element.style.height.replace('px', ''));
          var T = e.getElementSize(this.viewer.canvas);
          if (this.scroll === 'horizontal') {
            if (-d.delta.x > 0) {
              if (g > -(x - T.x)) {
                this.element.style.marginLeft = g + d.delta.x * 2 + 'px';
                a(this, T.x, g + d.delta.x * 2);
              }
            } else if (-d.delta.x < 0 && g < 0) {
              this.element.style.marginLeft = g + d.delta.x * 2 + 'px';
              a(this, T.x, g + d.delta.x * 2);
            }
          } else if (-d.delta.y > 0) {
            if (y > -(b - T.y)) {
              this.element.style.marginTop = y + d.delta.y * 2 + 'px';
              a(this, T.y, y + d.delta.y * 2);
            }
          } else if (-d.delta.y < 0 && y < 0) {
            this.element.style.marginTop = y + d.delta.y * 2 + 'px';
            a(this, T.y, y + d.delta.y * 2);
          }
        }
      }
      function s(d) {
        if (this.element) {
          var g = Number(this.element.style.marginLeft.replace('px', ''));
          var y = Number(this.element.style.marginTop.replace('px', ''));
          var x = Number(this.element.style.width.replace('px', ''));
          var b = Number(this.element.style.height.replace('px', ''));
          var T = e.getElementSize(this.viewer.canvas);
          if (this.scroll === 'horizontal') {
            if (d.scroll > 0) {
              if (g > -(x - T.x)) {
                this.element.style.marginLeft = g - d.scroll * 60 + 'px';
                a(this, T.x, g - d.scroll * 60);
              }
            } else if (d.scroll < 0 && g < 0) {
              this.element.style.marginLeft = g - d.scroll * 60 + 'px';
              a(this, T.x, g - d.scroll * 60);
            }
          } else if (d.scroll < 0) {
            if (y > T.y - b) {
              this.element.style.marginTop = y + d.scroll * 60 + 'px';
              a(this, T.y, y + d.scroll * 60);
            }
          } else if (d.scroll > 0 && y < 0) {
            this.element.style.marginTop = y + d.scroll * 60 + 'px';
            a(this, T.y, y + d.scroll * 60);
          }
          d.preventDefault = true;
        }
      }
      function a(d, g, y) {
        var x;
        var f;
        var A;
        if (d.scroll === 'horizontal') {
          x = d.panelWidth;
        } else {
          x = d.panelHeight;
        }
        var b = Math.ceil(g / x) + 5;
        var T = Math.ceil((Math.abs(y) + g) / x) + 1;
        b = T - b;
        b = b < 0 ? 0 : b;
        for (var E = b; E < T && E < d.panels.length; E++) {
          A = d.panels[E];
          if (!A.activePanel) {
            var C;
            var O = d.viewer.tileSources[E];
            if (O.referenceStripThumbnailUrl) {
              C = { type: 'image', url: O.referenceStripThumbnailUrl };
            } else {
              C = O;
            }
            f = new e.Viewer({
              id: A.id,
              tileSources: [C],
              element: A,
              navigatorSizeRatio: d.sizeRatio,
              showNavigator: false,
              mouseNavEnabled: false,
              showNavigationControl: false,
              showSequenceControl: false,
              immediateRender: true,
              blendTime: 0,
              animationTime: 0,
              loadTilesWithAjax: d.viewer.loadTilesWithAjax,
              ajaxHeaders: d.viewer.ajaxHeaders,
              useCanvas: d.useCanvas,
            });
            e.setElementPointerEventsNone(f.canvas);
            e.setElementPointerEventsNone(f.container);
            f.innerTracker.setTracking(false);
            f.outerTracker.setTracking(false);
            d.miniViewers[A.id] = f;
            A.activePanel = true;
          }
        }
      }
      function l(d) {
        var g = d.eventSource.element;
        if (this.scroll === 'horizontal') {
          g.style.marginBottom = '0px';
        } else {
          g.style.marginLeft = '0px';
        }
      }
      function u(d) {
        var g = d.eventSource.element;
        if (this.scroll === 'horizontal') {
          g.style.marginBottom = '-' + e.getElementSize(g).y / 2 + 'px';
        } else {
          g.style.marginLeft = '-' + e.getElementSize(g).x / 2 + 'px';
        }
      }
      function c(d) {
        if (!d.ctrl && !d.alt && !d.meta) {
          switch (d.keyCode) {
            case 38:
              s.call(this, {
                eventSource: this.tracker,
                position: null,
                scroll: 1,
                shift: null,
              });
              d.preventDefault = true;
              break;
            case 40:
              s.call(this, {
                eventSource: this.tracker,
                position: null,
                scroll: -1,
                shift: null,
              });
              d.preventDefault = true;
              break;
            case 37:
              s.call(this, {
                eventSource: this.tracker,
                position: null,
                scroll: -1,
                shift: null,
              });
              d.preventDefault = true;
              break;
            case 39:
              s.call(this, {
                eventSource: this.tracker,
                position: null,
                scroll: 1,
                shift: null,
              });
              d.preventDefault = true;
              break;
            default:
              d.preventDefault = false;
              break;
          }
        } else {
          d.preventDefault = false;
        }
      }
      function h(d) {
        if (!d.ctrl && !d.alt && !d.meta) {
          switch (d.keyCode) {
            case 61:
              s.call(this, {
                eventSource: this.tracker,
                position: null,
                scroll: 1,
                shift: null,
              });
              d.preventDefault = true;
              break;
            case 45:
              s.call(this, {
                eventSource: this.tracker,
                position: null,
                scroll: -1,
                shift: null,
              });
              d.preventDefault = true;
              break;
            case 48:
            case 119:
            case 87:
              s.call(this, {
                eventSource: this.tracker,
                position: null,
                scroll: 1,
                shift: null,
              });
              d.preventDefault = true;
              break;
            case 115:
            case 83:
              s.call(this, {
                eventSource: this.tracker,
                position: null,
                scroll: -1,
                shift: null,
              });
              d.preventDefault = true;
              break;
            case 97:
              s.call(this, {
                eventSource: this.tracker,
                position: null,
                scroll: -1,
                shift: null,
              });
              d.preventDefault = true;
              break;
            case 100:
              s.call(this, {
                eventSource: this.tracker,
                position: null,
                scroll: 1,
                shift: null,
              });
              d.preventDefault = true;
              break;
            default:
              d.preventDefault = false;
              break;
          }
        } else {
          d.preventDefault = false;
        }
      }
      var e = t;
      var n = {};
      e.ReferenceStrip = function (d) {
        var g = this;
        var y = d.viewer;
        var x = e.getElementSize(y.element);
        var b;
        if (!d.id) {
          d.id = 'referencestrip-' + e.now();
          this.element = e.makeNeutralElement('div');
          this.element.id = d.id;
          this.element.className = 'referencestrip';
        }
        d = e.extend(
          true,
          {
            sizeRatio: e.DEFAULT_SETTINGS.referenceStripSizeRatio,
            position: e.DEFAULT_SETTINGS.referenceStripPosition,
            scroll: e.DEFAULT_SETTINGS.referenceStripScroll,
            clickTimeThreshold: e.DEFAULT_SETTINGS.clickTimeThreshold,
          },
          d,
          { element: this.element }
        );
        e.extend(this, d);
        n[this.id] = { animating: false };
        this.minPixelRatio = this.viewer.minPixelRatio;
        this.element.tabIndex = 0;
        var T = this.element.style;
        T.marginTop = '0px';
        T.marginRight = '0px';
        T.marginBottom = '0px';
        T.marginLeft = '0px';
        T.left = '0px';
        T.bottom = '0px';
        T.border = '0px';
        T.background = '#000';
        T.position = 'relative';
        e.setElementTouchActionNone(this.element);
        e.setElementOpacity(this.element, 0.8);
        this.viewer = y;
        this.tracker = new e.MouseTracker({
          userData: 'ReferenceStrip.tracker',
          element: this.element,
          clickHandler: e.delegate(this, r),
          dragHandler: e.delegate(this, o),
          scrollHandler: e.delegate(this, s),
          enterHandler: e.delegate(this, l),
          leaveHandler: e.delegate(this, u),
          keyDownHandler: e.delegate(this, c),
          keyHandler: e.delegate(this, h),
          preProcessEventHandler: function (E) {
            if (E.eventType === 'wheel') {
              E.preventDefault = true;
            }
          },
        });
        if (d.width && d.height) {
          this.element.style.width = d.width + 'px';
          this.element.style.height = d.height + 'px';
          y.addControl(this.element, { anchor: e.ControlAnchor.BOTTOM_LEFT });
        } else if (d.scroll === 'horizontal') {
          this.element.style.width =
            x.x * d.sizeRatio * y.tileSources.length +
            12 * y.tileSources.length +
            'px';
          this.element.style.height = x.y * d.sizeRatio + 'px';
          y.addControl(this.element, { anchor: e.ControlAnchor.BOTTOM_LEFT });
        } else {
          this.element.style.height =
            x.y * d.sizeRatio * y.tileSources.length +
            12 * y.tileSources.length +
            'px';
          this.element.style.width = x.x * d.sizeRatio + 'px';
          y.addControl(this.element, { anchor: e.ControlAnchor.TOP_LEFT });
        }
        this.panelWidth = x.x * this.sizeRatio + 8;
        this.panelHeight = x.y * this.sizeRatio + 8;
        this.panels = [];
        this.miniViewers = {};
        for (var f = 0; f < y.tileSources.length; f++) {
          b = e.makeNeutralElement('div');
          b.id = this.element.id + '-' + f;
          b.style.width = g.panelWidth + 'px';
          b.style.height = g.panelHeight + 'px';
          b.style.display = 'inline';
          b.style.float = 'left';
          b.style.cssFloat = 'left';
          b.style.styleFloat = 'left';
          b.style.padding = '2px';
          e.setElementTouchActionNone(b);
          e.setElementPointerEventsNone(b);
          this.element.appendChild(b);
          b.activePanel = false;
          this.panels.push(b);
        }
        a(this, this.scroll === 'vertical' ? x.y : x.x, 0);
        this.setFocus(0);
      };
      e.ReferenceStrip.prototype = {
        setFocus: function (d) {
          var g = this.element.querySelector('#' + this.element.id + '-' + d);
          var y = e.getElementSize(this.viewer.canvas);
          var x = Number(this.element.style.width.replace('px', ''));
          var b = Number(this.element.style.height.replace('px', ''));
          var T = -Number(this.element.style.marginLeft.replace('px', ''));
          var f = -Number(this.element.style.marginTop.replace('px', ''));
          var E;
          if (this.currentSelected !== g) {
            if (this.currentSelected) {
              this.currentSelected.style.background = '#000';
            }
            this.currentSelected = g;
            this.currentSelected.style.background = '#999';
            if (this.scroll === 'horizontal') {
              E = Number(d) * (this.panelWidth + 3);
              if (E > T + y.x - this.panelWidth) {
                E = Math.min(E, x - y.x);
                this.element.style.marginLeft = -E + 'px';
                a(this, y.x, -E);
              } else if (E < T) {
                E = Math.max(0, E - y.x / 2);
                this.element.style.marginLeft = -E + 'px';
                a(this, y.x, -E);
              }
            } else {
              E = Number(d) * (this.panelHeight + 3);
              if (E > f + y.y - this.panelHeight) {
                E = Math.min(E, b - y.y);
                this.element.style.marginTop = -E + 'px';
                a(this, y.y, -E);
              } else if (E < f) {
                E = Math.max(0, E - y.y / 2);
                this.element.style.marginTop = -E + 'px';
                a(this, y.y, -E);
              }
            }
            this.currentPage = d;
            l.call(this, { eventSource: this.tracker });
          }
        },
        update: function () {
          if (n[this.id].animating) {
            e.console.log('image reference strip update');
            return true;
          } else {
            return false;
          }
        },
        destroy: function () {
          if (this.miniViewers) {
            for (var d in this.miniViewers) {
              this.miniViewers[d].destroy();
            }
          }
          this.tracker.destroy();
          if (this.element) {
            this.viewer.removeControl(this.element);
          }
        },
      };
    })();
    (function () {
      var e = t;
      e.DisplayRect = function (n, r, o, s, a, l) {
        e.Rect.apply(this, [n, r, o, s]);
        this.minLevel = a;
        this.maxLevel = l;
      };
      e.extend(e.DisplayRect.prototype, e.Rect.prototype);
    })();
    (function () {
      function n(r, o) {
        return (1 - Math.exp(r * -o)) / (1 - Math.exp(-r));
      }
      var e = t;
      e.Spring = function (r) {
        var o = arguments;
        if (typeof r != 'object') {
          r = {
            initial: o.length && typeof o[0] == 'number' ? o[0] : void 0,
            springStiffness: o.length > 1 ? o[1].springStiffness : 5,
            animationTime: o.length > 1 ? o[1].animationTime : 1.5,
          };
        }
        e.console.assert(
          typeof r.springStiffness == 'number' && r.springStiffness !== 0,
          '[OpenSeadragon.Spring] options.springStiffness must be a non-zero number'
        );
        e.console.assert(
          typeof r.animationTime == 'number' && r.animationTime >= 0,
          '[OpenSeadragon.Spring] options.animationTime must be a number greater than or equal to 0'
        );
        if (r.exponential) {
          this._exponential = true;
          delete r.exponential;
        }
        e.extend(true, this, r);
        this.current = {
          value:
            typeof this.initial == 'number'
              ? this.initial
              : this._exponential
              ? 0
              : 1,
          time: e.now(),
        };
        e.console.assert(
          !this._exponential || this.current.value !== 0,
          '[OpenSeadragon.Spring] value must be non-zero for exponential springs'
        );
        this.start = { value: this.current.value, time: this.current.time };
        this.target = { value: this.current.value, time: this.current.time };
        if (this._exponential) {
          this.start._logValue = Math.log(this.start.value);
          this.target._logValue = Math.log(this.target.value);
          this.current._logValue = Math.log(this.current.value);
        }
      };
      e.Spring.prototype = {
        resetTo: function (r) {
          e.console.assert(
            !this._exponential || r !== 0,
            '[OpenSeadragon.Spring.resetTo] target must be non-zero for exponential springs'
          );
          this.start.value = this.target.value = this.current.value = r;
          this.start.time = this.target.time = this.current.time = e.now();
          if (this._exponential) {
            this.start._logValue = Math.log(this.start.value);
            this.target._logValue = Math.log(this.target.value);
            this.current._logValue = Math.log(this.current.value);
          }
        },
        springTo: function (r) {
          e.console.assert(
            !this._exponential || r !== 0,
            '[OpenSeadragon.Spring.springTo] target must be non-zero for exponential springs'
          );
          this.start.value = this.current.value;
          this.start.time = this.current.time;
          this.target.value = r;
          this.target.time = this.start.time + 1e3 * this.animationTime;
          if (this._exponential) {
            this.start._logValue = Math.log(this.start.value);
            this.target._logValue = Math.log(this.target.value);
          }
        },
        shiftBy: function (r) {
          this.start.value += r;
          this.target.value += r;
          if (this._exponential) {
            e.console.assert(
              this.target.value !== 0 && this.start.value !== 0,
              '[OpenSeadragon.Spring.shiftBy] spring value must be non-zero for exponential springs'
            );
            this.start._logValue = Math.log(this.start.value);
            this.target._logValue = Math.log(this.target.value);
          }
        },
        setExponential: function (r) {
          this._exponential = r;
          if (this._exponential) {
            e.console.assert(
              this.current.value !== 0 &&
                this.target.value !== 0 &&
                this.start.value !== 0,
              '[OpenSeadragon.Spring.setExponential] spring value must be non-zero for exponential springs'
            );
            this.start._logValue = Math.log(this.start.value);
            this.target._logValue = Math.log(this.target.value);
            this.current._logValue = Math.log(this.current.value);
          }
        },
        update: function () {
          this.current.time = e.now();
          var r;
          var o;
          if (this._exponential) {
            r = this.start._logValue;
            o = this.target._logValue;
          } else {
            r = this.start.value;
            o = this.target.value;
          }
          var s =
            this.current.time >= this.target.time
              ? o
              : r +
                (o - r) *
                  n(
                    this.springStiffness,
                    (this.current.time - this.start.time) /
                      (this.target.time - this.start.time)
                  );
          var a = this.current.value;
          if (this._exponential) {
            this.current.value = Math.exp(s);
          } else {
            this.current.value = s;
          }
          return a !== this.current.value;
        },
        isAtTargetValue: function () {
          return this.current.value === this.target.value;
        },
      };
    })();
    (function () {
      function n(o) {
        e.extend(
          true,
          this,
          { timeout: e.DEFAULT_SETTINGS.timeout, jobId: null },
          o
        );
        this.image = null;
      }
      function r(o, s, a) {
        var l;
        o.jobsInProgress--;
        if (
          (!o.jobLimit || o.jobsInProgress < o.jobLimit) &&
          o.jobQueue.length > 0
        ) {
          l = o.jobQueue.shift();
          l.start();
          o.jobsInProgress++;
        }
        a(s.image, s.errorMsg, s.request);
      }
      var e = t;
      n.prototype = {
        errorMsg: null,
        start: function () {
          var o = this;
          var s = this.abort;
          this.image = new Image();
          this.image.onload = function () {
            o.finish(true);
          };
          this.image.onabort = this.image.onerror = function () {
            o.errorMsg = 'Image load aborted';
            o.finish(false);
          };
          this.jobId = window.setTimeout(function () {
            o.errorMsg = 'Image load exceeded timeout (' + o.timeout + ' ms)';
            o.finish(false);
          }, this.timeout);
          if (this.loadWithAjax) {
            this.request = e.makeAjaxRequest({
              url: this.src,
              withCredentials: this.ajaxWithCredentials,
              headers: this.ajaxHeaders,
              responseType: 'arraybuffer',
              success: function (a) {
                var l;
                try {
                  l = new window.Blob([a.response]);
                } catch (d) {
                  var u =
                    window.BlobBuilder ||
                    window.WebKitBlobBuilder ||
                    window.MozBlobBuilder ||
                    window.MSBlobBuilder;
                  if (d.name === 'TypeError' && u) {
                    var c = new u();
                    c.append(a.response);
                    l = c.getBlob();
                  }
                }
                if (l.size === 0) {
                  o.errorMsg = 'Empty image response.';
                  o.finish(false);
                }
                var h = (window.URL || window.webkitURL).createObjectURL(l);
                o.image.src = h;
              },
              error: function (a) {
                o.errorMsg = 'Image load aborted - XHR error';
                o.finish(false);
              },
            });
            this.abort = function () {
              o.request.abort();
              if (typeof s == 'function') {
                s();
              }
            };
          } else {
            if (this.crossOriginPolicy !== false) {
              this.image.crossOrigin = this.crossOriginPolicy;
            }
            this.image.src = this.src;
          }
        },
        finish: function (o) {
          this.image.onload = this.image.onerror = this.image.onabort = null;
          if (!o) {
            this.image = null;
          }
          if (this.jobId) {
            window.clearTimeout(this.jobId);
          }
          this.callback(this);
        },
      };
      e.ImageLoader = function (o) {
        e.extend(
          true,
          this,
          {
            jobLimit: e.DEFAULT_SETTINGS.imageLoaderLimit,
            timeout: e.DEFAULT_SETTINGS.timeout,
            jobQueue: [],
            jobsInProgress: 0,
          },
          o
        );
      };
      e.ImageLoader.prototype = {
        addJob: function (o) {
          var s = this;
          var a = function (c) {
            r(s, c, o.callback);
          };
          var l = {
            src: o.src,
            loadWithAjax: o.loadWithAjax,
            ajaxHeaders: o.loadWithAjax ? o.ajaxHeaders : null,
            crossOriginPolicy: o.crossOriginPolicy,
            ajaxWithCredentials: o.ajaxWithCredentials,
            callback: a,
            abort: o.abort,
            timeout: this.timeout,
          };
          var u = new n(l);
          if (!this.jobLimit || this.jobsInProgress < this.jobLimit) {
            u.start();
            this.jobsInProgress++;
          } else {
            this.jobQueue.push(u);
          }
        },
        clear: function () {
          for (var o = 0; o < this.jobQueue.length; o++) {
            var s = this.jobQueue[o];
            if (typeof s.abort == 'function') {
              s.abort();
            }
          }
          this.jobQueue = [];
        },
      };
    })();
    (function () {
      var e = t;
      e.Tile = function (n, r, o, s, a, l, u, c, h, d) {
        this.level = n;
        this.x = r;
        this.y = o;
        this.bounds = s;
        this.sourceBounds = d;
        this.exists = a;
        this.url = l;
        this.context2D = u;
        this.loadWithAjax = c;
        this.ajaxHeaders = h;
        if (this.ajaxHeaders) {
          this.cacheKey = this.url + '+' + JSON.stringify(this.ajaxHeaders);
        } else {
          this.cacheKey = this.url;
        }
        this.loaded = false;
        this.loading = false;
        this.element = null;
        this.imgElement = null;
        this.image = null;
        this.style = null;
        this.position = null;
        this.size = null;
        this.flipped = false;
        this.blendStart = null;
        this.opacity = null;
        this.squaredDistance = null;
        this.visibility = null;
        this.beingDrawn = false;
        this.lastTouchTime = 0;
        this.isRightMost = false;
        this.isBottomMost = false;
      };
      e.Tile.prototype = {
        toString: function () {
          return this.level + '/' + this.x + '_' + this.y;
        },
        _hasTransparencyChannel: function () {
          return !!this.context2D || this.url.match('.png');
        },
        drawHTML: function (n) {
          if (!this.cacheImageRecord) {
            e.console.warn(
              "[Tile.drawHTML] attempting to draw tile %s when it's not cached",
              this.toString()
            );
            return;
          }
          if (!this.loaded) {
            e.console.warn(
              "Attempting to draw tile %s when it's not yet loaded.",
              this.toString()
            );
            return;
          }
          if (!this.element) {
            this.element = e.makeNeutralElement('div');
            this.imgElement = this.cacheImageRecord.getImage().cloneNode();
            this.imgElement.style.msInterpolationMode = 'nearest-neighbor';
            this.imgElement.style.width = '100%';
            this.imgElement.style.height = '100%';
            this.style = this.element.style;
            this.style.position = 'absolute';
          }
          if (this.element.parentNode !== n) {
            n.appendChild(this.element);
          }
          if (this.imgElement.parentNode !== this.element) {
            this.element.appendChild(this.imgElement);
          }
          this.style.top = this.position.y + 'px';
          this.style.left = this.position.x + 'px';
          this.style.height = this.size.y + 'px';
          this.style.width = this.size.x + 'px';
          if (this.flipped) {
            this.style.transform = 'scaleX(-1)';
          }
          e.setElementOpacity(this.element, this.opacity);
        },
        drawCanvas: function (n, r, o, s) {
          var a = this.position.times(e.pixelDensityRatio);
          var l = this.size.times(e.pixelDensityRatio);
          if (!this.context2D && !this.cacheImageRecord) {
            e.console.warn(
              "[Tile.drawCanvas] attempting to draw tile %s when it's not cached",
              this.toString()
            );
            return;
          }
          var u = this.context2D || this.cacheImageRecord.getRenderedContext();
          if (!this.loaded || !u) {
            e.console.warn(
              "Attempting to draw tile %s when it's not yet loaded.",
              this.toString()
            );
            return;
          }
          n.save();
          n.globalAlpha = this.opacity;
          if (typeof o == 'number' && o !== 1) {
            a = a.times(o);
            l = l.times(o);
          }
          if (s instanceof e.Point) {
            a = a.plus(s);
          }
          if (n.globalAlpha === 1 && this._hasTransparencyChannel()) {
            n.clearRect(a.x, a.y, l.x, l.y);
          }
          r({ context: n, tile: this, rendered: u });
          var c;
          var h;
          if (this.sourceBounds) {
            c = Math.min(this.sourceBounds.width, u.canvas.width);
            h = Math.min(this.sourceBounds.height, u.canvas.height);
          } else {
            c = u.canvas.width;
            h = u.canvas.height;
          }
          n.translate(a.x + l.x / 2, 0);
          if (this.flipped) {
            n.scale(-1, 1);
          }
          n.drawImage(u.canvas, 0, 0, c, h, -l.x / 2, a.y, l.x, l.y);
          n.restore();
        },
        getScaleForEdgeSmoothing: function () {
          var n;
          if (this.cacheImageRecord) {
            n = this.cacheImageRecord.getRenderedContext();
          } else if (this.context2D) {
            n = this.context2D;
          } else {
            e.console.warn(
              "[Tile.drawCanvas] attempting to get tile scale %s when tile's not cached",
              this.toString()
            );
            return 1;
          }
          return n.canvas.width / (this.size.x * e.pixelDensityRatio);
        },
        getTranslationForEdgeSmoothing: function (n, r, o) {
          var s = Math.max(1, Math.ceil((o.x - r.x) / 2));
          var a = Math.max(1, Math.ceil((o.y - r.y) / 2));
          return new e.Point(s, a).minus(
            this.position
              .times(e.pixelDensityRatio)
              .times(n || 1)
              .apply(function (l) {
                return l % 1;
              })
          );
        },
        unload: function () {
          if (this.imgElement && this.imgElement.parentNode) {
            this.imgElement.parentNode.removeChild(this.imgElement);
          }
          if (this.element && this.element.parentNode) {
            this.element.parentNode.removeChild(this.element);
          }
          this.element = null;
          this.imgElement = null;
          this.loaded = false;
          this.loading = false;
        },
      };
    })();
    (function () {
      var e = t;
      e.OverlayPlacement = e.Placement;
      e.OverlayRotationMode = e.freezeObject({
        NO_ROTATION: 1,
        EXACT: 2,
        BOUNDING_BOX: 3,
      });
      e.Overlay = function (n, r, o) {
        var s;
        if (e.isPlainObject(n)) {
          s = n;
        } else {
          s = { element: n, location: r, placement: o };
        }
        this.element = s.element;
        this.style = s.element.style;
        this._init(s);
      };
      e.Overlay.prototype = {
        _init: function (n) {
          this.location = n.location;
          this.placement =
            n.placement === void 0 ? e.Placement.TOP_LEFT : n.placement;
          this.onDraw = n.onDraw;
          this.checkResize = n.checkResize === void 0 ? true : n.checkResize;
          this.width = n.width === void 0 ? null : n.width;
          this.height = n.height === void 0 ? null : n.height;
          this.rotationMode = n.rotationMode || e.OverlayRotationMode.EXACT;
          if (this.location instanceof e.Rect) {
            this.width = this.location.width;
            this.height = this.location.height;
            this.location = this.location.getTopLeft();
            this.placement = e.Placement.TOP_LEFT;
          }
          this.scales = this.width !== null && this.height !== null;
          this.bounds = new e.Rect(
            this.location.x,
            this.location.y,
            this.width,
            this.height
          );
          this.position = this.location;
        },
        adjust: function (n, r) {
          var o = e.Placement.properties[this.placement];
          if (o) {
            if (o.isHorizontallyCentered) {
              n.x -= r.x / 2;
            } else if (o.isRight) {
              n.x -= r.x;
            }
            if (o.isVerticallyCentered) {
              n.y -= r.y / 2;
            } else if (o.isBottom) {
              n.y -= r.y;
            }
          }
        },
        destroy: function () {
          var n = this.element;
          var r = this.style;
          if (n.parentNode) {
            n.parentNode.removeChild(n);
            if (n.prevElementParent) {
              r.display = 'none';
              document.body.appendChild(n);
            }
          }
          this.onDraw = null;
          r.top = '';
          r.left = '';
          r.position = '';
          if (this.width !== null) {
            r.width = '';
          }
          if (this.height !== null) {
            r.height = '';
          }
          var o = e.getCssPropertyWithVendorPrefix('transformOrigin');
          var s = e.getCssPropertyWithVendorPrefix('transform');
          if (o && s) {
            r[o] = '';
            r[s] = '';
          }
        },
        drawHTML: function (n, r) {
          var o = this.element;
          if (o.parentNode !== n) {
            o.prevElementParent = o.parentNode;
            o.prevNextSibling = o.nextSibling;
            n.appendChild(o);
            this.style.position = 'absolute';
            this.size = e.getElementSize(o);
          }
          var s = this._getOverlayPositionAndSize(r);
          var a = s.position;
          var l = (this.size = s.size);
          var u = s.rotate;
          if (this.onDraw) {
            this.onDraw(a, l, this.element);
          } else {
            var c = this.style;
            c.left = a.x + 'px';
            c.top = a.y + 'px';
            if (this.width !== null) {
              c.width = l.x + 'px';
            }
            if (this.height !== null) {
              c.height = l.y + 'px';
            }
            var h = e.getCssPropertyWithVendorPrefix('transformOrigin');
            var d = e.getCssPropertyWithVendorPrefix('transform');
            if (h && d) {
              if (u) {
                c[h] = this._getTransformOrigin();
                c[d] = 'rotate(' + u + 'deg)';
              } else {
                c[h] = '';
                c[d] = '';
              }
            }
            c.display = 'block';
          }
        },
        _getOverlayPositionAndSize: function (n) {
          var r = n.pixelFromPoint(this.location, true);
          var o = this._getSizeInPixels(n);
          this.adjust(r, o);
          var s = 0;
          if (
            n.degrees &&
            this.rotationMode !== e.OverlayRotationMode.NO_ROTATION
          ) {
            if (
              this.rotationMode === e.OverlayRotationMode.BOUNDING_BOX &&
              this.width !== null &&
              this.height !== null
            ) {
              var a = new e.Rect(r.x, r.y, o.x, o.y);
              var l = this._getBoundingBox(a, n.degrees);
              r = l.getTopLeft();
              o = l.getSize();
            } else {
              s = n.degrees;
            }
          }
          return { position: r, size: o, rotate: s };
        },
        _getSizeInPixels: function (n) {
          var r = this.size.x;
          var o = this.size.y;
          if (this.width !== null || this.height !== null) {
            var s = n.deltaPixelsFromPointsNoRotate(
              new e.Point(this.width || 0, this.height || 0),
              true
            );
            if (this.width !== null) {
              r = s.x;
            }
            if (this.height !== null) {
              o = s.y;
            }
          }
          if (
            this.checkResize &&
            (this.width === null || this.height === null)
          ) {
            var a = (this.size = e.getElementSize(this.element));
            if (this.width === null) {
              r = a.x;
            }
            if (this.height === null) {
              o = a.y;
            }
          }
          return new e.Point(r, o);
        },
        _getBoundingBox: function (n, r) {
          var o = this._getPlacementPoint(n);
          return n.rotate(r, o).getBoundingBox();
        },
        _getPlacementPoint: function (n) {
          var r = new e.Point(n.x, n.y);
          var o = e.Placement.properties[this.placement];
          if (o) {
            if (o.isHorizontallyCentered) {
              r.x += n.width / 2;
            } else if (o.isRight) {
              r.x += n.width;
            }
            if (o.isVerticallyCentered) {
              r.y += n.height / 2;
            } else if (o.isBottom) {
              r.y += n.height;
            }
          }
          return r;
        },
        _getTransformOrigin: function () {
          var n = '';
          var r = e.Placement.properties[this.placement];
          if (r) {
            if (r.isLeft) {
              n = 'left';
            } else if (r.isRight) {
              n = 'right';
            }
            if (r.isTop) {
              n += ' top';
            } else if (r.isBottom) {
              n += ' bottom';
            }
          }
          return n;
        },
        update: function (n, r) {
          var o = e.isPlainObject(n) ? n : { location: n, placement: r };
          this._init({
            location: o.location || this.location,
            placement: o.placement !== void 0 ? o.placement : this.placement,
            onDraw: o.onDraw || this.onDraw,
            checkResize: o.checkResize || this.checkResize,
            width: o.width !== void 0 ? o.width : this.width,
            height: o.height !== void 0 ? o.height : this.height,
            rotationMode: o.rotationMode || this.rotationMode,
          });
        },
        getBounds: function (n) {
          e.console.assert(
            n,
            'A viewport must now be passed to Overlay.getBounds.'
          );
          var r = this.width;
          var o = this.height;
          if (r === null || o === null) {
            var s = n.deltaPointsFromPixelsNoRotate(this.size, true);
            if (r === null) {
              r = s.x;
            }
            if (o === null) {
              o = s.y;
            }
          }
          var a = this.location.clone();
          this.adjust(a, new e.Point(r, o));
          return this._adjustBoundsForRotation(n, new e.Rect(a.x, a.y, r, o));
        },
        _adjustBoundsForRotation: function (n, r) {
          if (
            !n ||
            n.degrees === 0 ||
            this.rotationMode === e.OverlayRotationMode.EXACT
          ) {
            return r;
          }
          if (this.rotationMode === e.OverlayRotationMode.BOUNDING_BOX) {
            if (this.width === null || this.height === null) {
              return r;
            }
            var o = this._getOverlayPositionAndSize(n);
            return n.viewerElementToViewportRectangle(
              new e.Rect(o.position.x, o.position.y, o.size.x, o.size.y)
            );
          }
          return r.rotate(-n.degrees, this._getPlacementPoint(r));
        },
      };
    })();
    (function () {
      var e = t;
      e.Drawer = function (n) {
        e.console.assert(n.viewer, '[Drawer] options.viewer is required');
        var r = arguments;
        if (!e.isPlainObject(n)) {
          n = { source: r[0], viewport: r[1], element: r[2] };
        }
        e.console.assert(n.viewport, '[Drawer] options.viewport is required');
        e.console.assert(n.element, '[Drawer] options.element is required');
        if (n.source) {
          e.console.error(
            '[Drawer] options.source is no longer accepted; use TiledImage instead'
          );
        }
        this.viewer = n.viewer;
        this.viewport = n.viewport;
        this.debugGridColor =
          typeof n.debugGridColor == 'string'
            ? [n.debugGridColor]
            : n.debugGridColor || e.DEFAULT_SETTINGS.debugGridColor;
        if (n.opacity) {
          e.console.error(
            '[Drawer] options.opacity is no longer accepted; set the opacity on the TiledImage instead'
          );
        }
        this.useCanvas =
          e.supportsCanvas && (this.viewer ? this.viewer.useCanvas : true);
        this.container = e.getElement(n.element);
        this.canvas = e.makeNeutralElement(this.useCanvas ? 'canvas' : 'div');
        this.context = this.useCanvas ? this.canvas.getContext('2d') : null;
        this.sketchCanvas = null;
        this.sketchContext = null;
        this.element = this.container;
        this.container.dir = 'ltr';
        if (this.useCanvas) {
          var o = this._calculateCanvasSize();
          this.canvas.width = o.x;
          this.canvas.height = o.y;
        }
        this.canvas.style.width = '100%';
        this.canvas.style.height = '100%';
        this.canvas.style.position = 'absolute';
        e.setElementOpacity(this.canvas, this.opacity, true);
        e.setElementPointerEventsNone(this.canvas);
        e.setElementTouchActionNone(this.canvas);
        this.container.style.textAlign = 'left';
        this.container.appendChild(this.canvas);
        this._imageSmoothingEnabled = true;
      };
      e.Drawer.prototype = {
        addOverlay: function (n, r, o, s) {
          e.console.error(
            'drawer.addOverlay is deprecated. Use viewer.addOverlay instead.'
          );
          this.viewer.addOverlay(n, r, o, s);
          return this;
        },
        updateOverlay: function (n, r, o) {
          e.console.error(
            'drawer.updateOverlay is deprecated. Use viewer.updateOverlay instead.'
          );
          this.viewer.updateOverlay(n, r, o);
          return this;
        },
        removeOverlay: function (n) {
          e.console.error(
            'drawer.removeOverlay is deprecated. Use viewer.removeOverlay instead.'
          );
          this.viewer.removeOverlay(n);
          return this;
        },
        clearOverlays: function () {
          e.console.error(
            'drawer.clearOverlays is deprecated. Use viewer.clearOverlays instead.'
          );
          this.viewer.clearOverlays();
          return this;
        },
        viewportCoordToDrawerCoord: function (n) {
          var r = this.viewport.pixelFromPointNoRotate(n, true);
          return new e.Point(
            r.x * e.pixelDensityRatio,
            r.y * e.pixelDensityRatio
          );
        },
        clipWithPolygons: function (n, r) {
          if (this.useCanvas) {
            var o = this._getContext(r);
            o.beginPath();
            n.forEach(function (s) {
              s.forEach(function (a, l) {
                o[l === 0 ? 'moveTo' : 'lineTo'](a.x, a.y);
              });
            });
            o.clip();
          }
        },
        setOpacity: function (n) {
          e.console.error(
            'drawer.setOpacity is deprecated. Use tiledImage.setOpacity instead.'
          );
          var r = this.viewer.world;
          for (var o = 0; o < r.getItemCount(); o++) {
            r.getItemAt(o).setOpacity(n);
          }
          return this;
        },
        getOpacity: function () {
          e.console.error(
            'drawer.getOpacity is deprecated. Use tiledImage.getOpacity instead.'
          );
          var n = this.viewer.world;
          var r = 0;
          for (var o = 0; o < n.getItemCount(); o++) {
            var s = n.getItemAt(o).getOpacity();
            if (s > r) {
              r = s;
            }
          }
          return r;
        },
        needsUpdate: function () {
          e.console.error(
            '[Drawer.needsUpdate] this function is deprecated. Use World.needsDraw instead.'
          );
          return this.viewer.world.needsDraw();
        },
        numTilesLoaded: function () {
          e.console.error(
            '[Drawer.numTilesLoaded] this function is deprecated. Use TileCache.numTilesLoaded instead.'
          );
          return this.viewer.tileCache.numTilesLoaded();
        },
        reset: function () {
          e.console.error(
            '[Drawer.reset] this function is deprecated. Use World.resetItems instead.'
          );
          this.viewer.world.resetItems();
          return this;
        },
        update: function () {
          e.console.error(
            '[Drawer.update] this function is deprecated. Use Drawer.clear and World.draw instead.'
          );
          this.clear();
          this.viewer.world.draw();
          return this;
        },
        canRotate: function () {
          return this.useCanvas;
        },
        destroy: function () {
          this.canvas.width = 1;
          this.canvas.height = 1;
          this.sketchCanvas = null;
          this.sketchContext = null;
        },
        clear: function () {
          this.canvas.innerHTML = '';
          if (this.useCanvas) {
            var n = this._calculateCanvasSize();
            if (this.canvas.width !== n.x || this.canvas.height !== n.y) {
              this.canvas.width = n.x;
              this.canvas.height = n.y;
              this._updateImageSmoothingEnabled(this.context);
              if (this.sketchCanvas !== null) {
                var r = this._calculateSketchCanvasSize();
                this.sketchCanvas.width = r.x;
                this.sketchCanvas.height = r.y;
                this._updateImageSmoothingEnabled(this.sketchContext);
              }
            }
            this._clear();
          }
        },
        _clear: function (n, r) {
          if (this.useCanvas) {
            var o = this._getContext(n);
            if (r) {
              o.clearRect(r.x, r.y, r.width, r.height);
            } else {
              var s = o.canvas;
              o.clearRect(0, 0, s.width, s.height);
            }
          }
        },
        viewportToDrawerRectangle: function (n) {
          var r = this.viewport.pixelFromPointNoRotate(n.getTopLeft(), true);
          var o = this.viewport.deltaPixelsFromPointsNoRotate(
            n.getSize(),
            true
          );
          return new e.Rect(
            r.x * e.pixelDensityRatio,
            r.y * e.pixelDensityRatio,
            o.x * e.pixelDensityRatio,
            o.y * e.pixelDensityRatio
          );
        },
        drawTile: function (n, r, o, s, a) {
          e.console.assert(n, '[Drawer.drawTile] tile is required');
          e.console.assert(r, '[Drawer.drawTile] drawingHandler is required');
          if (this.useCanvas) {
            var l = this._getContext(o);
            s = s || 1;
            n.drawCanvas(l, r, s, a);
          } else {
            n.drawHTML(this.canvas);
          }
        },
        _getContext: function (n) {
          var r = this.context;
          if (n) {
            if (this.sketchCanvas === null) {
              this.sketchCanvas = document.createElement('canvas');
              var o = this._calculateSketchCanvasSize();
              this.sketchCanvas.width = o.x;
              this.sketchCanvas.height = o.y;
              this.sketchContext = this.sketchCanvas.getContext('2d');
              if (this.viewport.getRotation() === 0) {
                var s = this;
                this.viewer.addHandler('rotate', function a() {
                  if (s.viewport.getRotation() !== 0) {
                    s.viewer.removeHandler('rotate', a);
                    var l = s._calculateSketchCanvasSize();
                    s.sketchCanvas.width = l.x;
                    s.sketchCanvas.height = l.y;
                  }
                });
              }
              this._updateImageSmoothingEnabled(this.sketchContext);
            }
            r = this.sketchContext;
          }
          return r;
        },
        saveContext: function (n) {
          if (this.useCanvas) {
            this._getContext(n).save();
          }
        },
        restoreContext: function (n) {
          if (this.useCanvas) {
            this._getContext(n).restore();
          }
        },
        setClip: function (n, r) {
          if (this.useCanvas) {
            var o = this._getContext(r);
            o.beginPath();
            o.rect(n.x, n.y, n.width, n.height);
            o.clip();
          }
        },
        drawRectangle: function (n, r, o) {
          if (this.useCanvas) {
            var s = this._getContext(o);
            s.save();
            s.fillStyle = r;
            s.fillRect(n.x, n.y, n.width, n.height);
            s.restore();
          }
        },
        blendSketch: function (n, r, o, s) {
          var a = n;
          if (!e.isPlainObject(a)) {
            a = { opacity: n, scale: r, translate: o, compositeOperation: s };
          }
          if (!!this.useCanvas && !!this.sketchCanvas) {
            n = a.opacity;
            s = a.compositeOperation;
            var l = a.bounds;
            this.context.save();
            this.context.globalAlpha = n;
            if (s) {
              this.context.globalCompositeOperation = s;
            }
            if (l) {
              if (l.x < 0) {
                l.width += l.x;
                l.x = 0;
              }
              if (l.x + l.width > this.canvas.width) {
                l.width = this.canvas.width - l.x;
              }
              if (l.y < 0) {
                l.height += l.y;
                l.y = 0;
              }
              if (l.y + l.height > this.canvas.height) {
                l.height = this.canvas.height - l.y;
              }
              this.context.drawImage(
                this.sketchCanvas,
                l.x,
                l.y,
                l.width,
                l.height,
                l.x,
                l.y,
                l.width,
                l.height
              );
            } else {
              r = a.scale || 1;
              o = a.translate;
              var u = o instanceof e.Point ? o : new e.Point(0, 0);
              var c = 0;
              var h = 0;
              if (o) {
                var d = this.sketchCanvas.width - this.canvas.width;
                var g = this.sketchCanvas.height - this.canvas.height;
                c = Math.round(d / 2);
                h = Math.round(g / 2);
              }
              this.context.drawImage(
                this.sketchCanvas,
                u.x - c * r,
                u.y - h * r,
                (this.canvas.width + 2 * c) * r,
                (this.canvas.height + 2 * h) * r,
                -c,
                -h,
                this.canvas.width + 2 * c,
                this.canvas.height + 2 * h
              );
            }
            this.context.restore();
          }
        },
        drawDebugInfo: function (n, r, o, s) {
          if (this.useCanvas) {
            var a =
              this.viewer.world.getIndexOfItem(s) % this.debugGridColor.length;
            var l = this.context;
            l.save();
            l.lineWidth = 2 * e.pixelDensityRatio;
            l.font = 'small-caps bold ' + 13 * e.pixelDensityRatio + 'px arial';
            l.strokeStyle = this.debugGridColor[a];
            l.fillStyle = this.debugGridColor[a];
            if (this.viewport.degrees !== 0) {
              this._offsetForRotation({ degrees: this.viewport.degrees });
            }
            if (s.getRotation(true) % 360 != 0) {
              this._offsetForRotation({
                degrees: s.getRotation(true),
                point: s.viewport.pixelFromPointNoRotate(
                  s._getRotationPoint(true),
                  true
                ),
              });
            }
            if (
              s.viewport.degrees === 0 &&
              s.getRotation(true) % 360 == 0 &&
              s._drawer.viewer.viewport.getFlip()
            ) {
              s._drawer._flip();
            }
            l.strokeRect(
              n.position.x * e.pixelDensityRatio,
              n.position.y * e.pixelDensityRatio,
              n.size.x * e.pixelDensityRatio,
              n.size.y * e.pixelDensityRatio
            );
            var u = (n.position.x + n.size.x / 2) * e.pixelDensityRatio;
            var c = (n.position.y + n.size.y / 2) * e.pixelDensityRatio;
            l.translate(u, c);
            l.rotate((Math.PI / 180) * -this.viewport.degrees);
            l.translate(-u, -c);
            if (n.x === 0 && n.y === 0) {
              l.fillText(
                'Zoom: ' + this.viewport.getZoom(),
                n.position.x * e.pixelDensityRatio,
                (n.position.y - 30) * e.pixelDensityRatio
              );
              l.fillText(
                'Pan: ' + this.viewport.getBounds().toString(),
                n.position.x * e.pixelDensityRatio,
                (n.position.y - 20) * e.pixelDensityRatio
              );
            }
            l.fillText(
              'Level: ' + n.level,
              (n.position.x + 10) * e.pixelDensityRatio,
              (n.position.y + 20) * e.pixelDensityRatio
            );
            l.fillText(
              'Column: ' + n.x,
              (n.position.x + 10) * e.pixelDensityRatio,
              (n.position.y + 30) * e.pixelDensityRatio
            );
            l.fillText(
              'Row: ' + n.y,
              (n.position.x + 10) * e.pixelDensityRatio,
              (n.position.y + 40) * e.pixelDensityRatio
            );
            l.fillText(
              'Order: ' + o + ' of ' + r,
              (n.position.x + 10) * e.pixelDensityRatio,
              (n.position.y + 50) * e.pixelDensityRatio
            );
            l.fillText(
              'Size: ' + n.size.toString(),
              (n.position.x + 10) * e.pixelDensityRatio,
              (n.position.y + 60) * e.pixelDensityRatio
            );
            l.fillText(
              'Position: ' + n.position.toString(),
              (n.position.x + 10) * e.pixelDensityRatio,
              (n.position.y + 70) * e.pixelDensityRatio
            );
            if (this.viewport.degrees !== 0) {
              this._restoreRotationChanges();
            }
            if (s.getRotation(true) % 360 != 0) {
              this._restoreRotationChanges();
            }
            if (
              s.viewport.degrees === 0 &&
              s.getRotation(true) % 360 == 0 &&
              s._drawer.viewer.viewport.getFlip()
            ) {
              s._drawer._flip();
            }
            l.restore();
          }
        },
        debugRect: function (n) {
          if (this.useCanvas) {
            var r = this.context;
            r.save();
            r.lineWidth = 2 * e.pixelDensityRatio;
            r.strokeStyle = this.debugGridColor[0];
            r.fillStyle = this.debugGridColor[0];
            r.strokeRect(
              n.x * e.pixelDensityRatio,
              n.y * e.pixelDensityRatio,
              n.width * e.pixelDensityRatio,
              n.height * e.pixelDensityRatio
            );
            r.restore();
          }
        },
        setImageSmoothingEnabled: function (n) {
          if (this.useCanvas) {
            this._imageSmoothingEnabled = n;
            this._updateImageSmoothingEnabled(this.context);
            this.viewer.forceRedraw();
          }
        },
        _updateImageSmoothingEnabled: function (n) {
          n.msImageSmoothingEnabled = this._imageSmoothingEnabled;
          n.imageSmoothingEnabled = this._imageSmoothingEnabled;
        },
        getCanvasSize: function (n) {
          var r = this._getContext(n).canvas;
          return new e.Point(r.width, r.height);
        },
        getCanvasCenter: function () {
          return new e.Point(this.canvas.width / 2, this.canvas.height / 2);
        },
        _offsetForRotation: function (n) {
          var r = n.point
            ? n.point.times(e.pixelDensityRatio)
            : this.getCanvasCenter();
          var o = this._getContext(n.useSketch);
          o.save();
          o.translate(r.x, r.y);
          if (this.viewer.viewport.flipped) {
            o.rotate((Math.PI / 180) * -n.degrees);
            o.scale(-1, 1);
          } else {
            o.rotate((Math.PI / 180) * n.degrees);
          }
          o.translate(-r.x, -r.y);
        },
        _flip: function (n) {
          n = n || {};
          var r = n.point
            ? n.point.times(e.pixelDensityRatio)
            : this.getCanvasCenter();
          var o = this._getContext(n.useSketch);
          o.translate(r.x, 0);
          o.scale(-1, 1);
          o.translate(-r.x, 0);
        },
        _restoreRotationChanges: function (n) {
          var r = this._getContext(n);
          r.restore();
        },
        _calculateCanvasSize: function () {
          var n = e.pixelDensityRatio;
          var r = this.viewport.getContainerSize();
          return { x: Math.round(r.x * n), y: Math.round(r.y * n) };
        },
        _calculateSketchCanvasSize: function () {
          var n = this._calculateCanvasSize();
          if (this.viewport.getRotation() === 0) {
            return n;
          }
          var r = Math.ceil(Math.sqrt(n.x * n.x + n.y * n.y));
          return { x: r, y: r };
        },
      };
    })();
    (function () {
      var e = t;
      e.Viewport = function (n) {
        var r = arguments;
        if (r.length && r[0] instanceof e.Point) {
          n = { containerSize: r[0], contentSize: r[1], config: r[2] };
        }
        if (n.config) {
          e.extend(true, n, n.config);
          delete n.config;
        }
        this._margins = e.extend(
          { left: 0, top: 0, right: 0, bottom: 0 },
          n.margins || {}
        );
        delete n.margins;
        e.extend(
          true,
          this,
          {
            containerSize: null,
            contentSize: null,
            zoomPoint: null,
            viewer: null,
            springStiffness: e.DEFAULT_SETTINGS.springStiffness,
            animationTime: e.DEFAULT_SETTINGS.animationTime,
            minZoomImageRatio: e.DEFAULT_SETTINGS.minZoomImageRatio,
            maxZoomPixelRatio: e.DEFAULT_SETTINGS.maxZoomPixelRatio,
            visibilityRatio: e.DEFAULT_SETTINGS.visibilityRatio,
            wrapHorizontal: e.DEFAULT_SETTINGS.wrapHorizontal,
            wrapVertical: e.DEFAULT_SETTINGS.wrapVertical,
            defaultZoomLevel: e.DEFAULT_SETTINGS.defaultZoomLevel,
            minZoomLevel: e.DEFAULT_SETTINGS.minZoomLevel,
            maxZoomLevel: e.DEFAULT_SETTINGS.maxZoomLevel,
            degrees: e.DEFAULT_SETTINGS.degrees,
            flipped: e.DEFAULT_SETTINGS.flipped,
            homeFillsViewer: e.DEFAULT_SETTINGS.homeFillsViewer,
          },
          n
        );
        this._updateContainerInnerSize();
        this.centerSpringX = new e.Spring({
          initial: 0,
          springStiffness: this.springStiffness,
          animationTime: this.animationTime,
        });
        this.centerSpringY = new e.Spring({
          initial: 0,
          springStiffness: this.springStiffness,
          animationTime: this.animationTime,
        });
        this.zoomSpring = new e.Spring({
          exponential: true,
          initial: 1,
          springStiffness: this.springStiffness,
          animationTime: this.animationTime,
        });
        this._oldCenterX = this.centerSpringX.current.value;
        this._oldCenterY = this.centerSpringY.current.value;
        this._oldZoom = this.zoomSpring.current.value;
        this._setContentBounds(new e.Rect(0, 0, 1, 1), 1);
        this.goHome(true);
        this.update();
      };
      e.Viewport.prototype = {
        resetContentSize: function (n) {
          e.console.assert(
            n,
            '[Viewport.resetContentSize] contentSize is required'
          );
          e.console.assert(
            n instanceof e.Point,
            '[Viewport.resetContentSize] contentSize must be an OpenSeadragon.Point'
          );
          e.console.assert(
            n.x > 0,
            '[Viewport.resetContentSize] contentSize.x must be greater than 0'
          );
          e.console.assert(
            n.y > 0,
            '[Viewport.resetContentSize] contentSize.y must be greater than 0'
          );
          this._setContentBounds(new e.Rect(0, 0, 1, n.y / n.x), n.x);
          return this;
        },
        setHomeBounds: function (n, r) {
          e.console.error(
            '[Viewport.setHomeBounds] this function is deprecated; The content bounds should not be set manually.'
          );
          this._setContentBounds(n, r);
        },
        _setContentBounds: function (n, r) {
          e.console.assert(
            n,
            '[Viewport._setContentBounds] bounds is required'
          );
          e.console.assert(
            n instanceof e.Rect,
            '[Viewport._setContentBounds] bounds must be an OpenSeadragon.Rect'
          );
          e.console.assert(
            n.width > 0,
            '[Viewport._setContentBounds] bounds.width must be greater than 0'
          );
          e.console.assert(
            n.height > 0,
            '[Viewport._setContentBounds] bounds.height must be greater than 0'
          );
          this._contentBoundsNoRotate = n.clone();
          this._contentSizeNoRotate = this._contentBoundsNoRotate
            .getSize()
            .times(r);
          this._contentBounds = n.rotate(this.degrees).getBoundingBox();
          this._contentSize = this._contentBounds.getSize().times(r);
          this._contentAspectRatio = this._contentSize.x / this._contentSize.y;
          if (this.viewer) {
            this.viewer.raiseEvent('reset-size', {
              contentSize: this._contentSizeNoRotate.clone(),
              contentFactor: r,
              homeBounds: this._contentBoundsNoRotate.clone(),
              contentBounds: this._contentBounds.clone(),
            });
          }
        },
        getHomeZoom: function () {
          if (this.defaultZoomLevel) {
            return this.defaultZoomLevel;
          }
          var n = this._contentAspectRatio / this.getAspectRatio();
          var r;
          if (this.homeFillsViewer) {
            r = n >= 1 ? n : 1;
          } else {
            r = n >= 1 ? 1 : n;
          }
          return r / this._contentBounds.width;
        },
        getHomeBounds: function () {
          return this.getHomeBoundsNoRotate().rotate(-this.getRotation());
        },
        getHomeBoundsNoRotate: function () {
          var n = this._contentBounds.getCenter();
          var r = 1 / this.getHomeZoom();
          var o = r / this.getAspectRatio();
          return new e.Rect(n.x - r / 2, n.y - o / 2, r, o);
        },
        goHome: function (n) {
          if (this.viewer) {
            this.viewer.raiseEvent('home', { immediately: n });
          }
          return this.fitBounds(this.getHomeBounds(), n);
        },
        getMinZoom: function () {
          var n = this.getHomeZoom();
          var r = this.minZoomLevel
            ? this.minZoomLevel
            : this.minZoomImageRatio * n;
          return r;
        },
        getMaxZoom: function () {
          var n = this.maxZoomLevel;
          if (!n) {
            n =
              (this._contentSize.x * this.maxZoomPixelRatio) /
              this._containerInnerSize.x;
            n /= this._contentBounds.width;
          }
          return Math.max(n, this.getHomeZoom());
        },
        getAspectRatio: function () {
          return this._containerInnerSize.x / this._containerInnerSize.y;
        },
        getContainerSize: function () {
          return new e.Point(this.containerSize.x, this.containerSize.y);
        },
        getMargins: function () {
          return e.extend({}, this._margins);
        },
        setMargins: function (n) {
          e.console.assert(
            e.type(n) === 'object',
            '[Viewport.setMargins] margins must be an object'
          );
          this._margins = e.extend({ left: 0, top: 0, right: 0, bottom: 0 }, n);
          this._updateContainerInnerSize();
          if (this.viewer) {
            this.viewer.forceRedraw();
          }
        },
        getBounds: function (n) {
          return this.getBoundsNoRotate(n).rotate(-this.getRotation());
        },
        getBoundsNoRotate: function (n) {
          var r = this.getCenter(n);
          var o = 1 / this.getZoom(n);
          var s = o / this.getAspectRatio();
          return new e.Rect(r.x - o / 2, r.y - s / 2, o, s);
        },
        getBoundsWithMargins: function (n) {
          return this.getBoundsNoRotateWithMargins(n).rotate(
            -this.getRotation(),
            this.getCenter(n)
          );
        },
        getBoundsNoRotateWithMargins: function (n) {
          var r = this.getBoundsNoRotate(n);
          var o = this._containerInnerSize.x * this.getZoom(n);
          r.x -= this._margins.left / o;
          r.y -= this._margins.top / o;
          r.width += (this._margins.left + this._margins.right) / o;
          r.height += (this._margins.top + this._margins.bottom) / o;
          return r;
        },
        getCenter: function (n) {
          var r = new e.Point(
            this.centerSpringX.current.value,
            this.centerSpringY.current.value
          );
          var o = new e.Point(
            this.centerSpringX.target.value,
            this.centerSpringY.target.value
          );
          var s;
          var a;
          var l;
          var u;
          var c;
          var h;
          var d;
          var g;
          if (n) {
            return r;
          } else if (this.zoomPoint) {
            s = this.pixelFromPoint(this.zoomPoint, true);
            a = this.getZoom();
            l = 1 / a;
            u = l / this.getAspectRatio();
            c = new e.Rect(r.x - l / 2, r.y - u / 2, l, u);
            h = this._pixelFromPoint(this.zoomPoint, c);
            d = h.minus(s);
            g = d.divide(this._containerInnerSize.x * a);
            return o.plus(g);
          } else {
            return o;
          }
        },
        getZoom: function (n) {
          if (n) {
            return this.zoomSpring.current.value;
          } else {
            return this.zoomSpring.target.value;
          }
        },
        _applyZoomConstraints: function (n) {
          return Math.max(Math.min(n, this.getMaxZoom()), this.getMinZoom());
        },
        _applyBoundaryConstraints: function (n) {
          var r = new e.Rect(n.x, n.y, n.width, n.height);
          if (!this.wrapHorizontal) {
            var o = this.visibilityRatio * r.width;
            var s = r.x + r.width;
            var a =
              this._contentBoundsNoRotate.x + this._contentBoundsNoRotate.width;
            var l = this._contentBoundsNoRotate.x - s + o;
            var u = a - r.x - o;
            if (o > this._contentBoundsNoRotate.width) {
              r.x += (l + u) / 2;
            } else if (u < 0) {
              r.x += u;
            } else if (l > 0) {
              r.x += l;
            }
          }
          if (!this.wrapVertical) {
            var c = this.visibilityRatio * r.height;
            var h = r.y + r.height;
            var d =
              this._contentBoundsNoRotate.y +
              this._contentBoundsNoRotate.height;
            var g = this._contentBoundsNoRotate.y - h + c;
            var y = d - r.y - c;
            if (c > this._contentBoundsNoRotate.height) {
              r.y += (g + y) / 2;
            } else if (y < 0) {
              r.y += y;
            } else if (g > 0) {
              r.y += g;
            }
          }
          return r;
        },
        _raiseConstraintsEvent: function (n) {
          if (this.viewer) {
            this.viewer.raiseEvent('constrain', { immediately: n });
          }
        },
        applyConstraints: function (n) {
          var r = this.getZoom();
          var o = this._applyZoomConstraints(r);
          if (r !== o) {
            this.zoomTo(o, this.zoomPoint, n);
          }
          var s = this.getBoundsNoRotate();
          var a = this._applyBoundaryConstraints(s);
          this._raiseConstraintsEvent(n);
          if (s.x !== a.x || s.y !== a.y || n) {
            this.fitBounds(a.rotate(-this.getRotation()), n);
          }
          return this;
        },
        ensureVisible: function (n) {
          return this.applyConstraints(n);
        },
        _fitBounds: function (n, r) {
          r = r || {};
          var o = r.immediately || false;
          var s = r.constraints || false;
          var a = this.getAspectRatio();
          var l = n.getCenter();
          var u = new e.Rect(
            n.x,
            n.y,
            n.width,
            n.height,
            n.degrees + this.getRotation()
          ).getBoundingBox();
          if (u.getAspectRatio() >= a) {
            u.height = u.width / a;
          } else {
            u.width = u.height * a;
          }
          u.x = l.x - u.width / 2;
          u.y = l.y - u.height / 2;
          var c = 1 / u.width;
          if (s) {
            var h = u.getAspectRatio();
            var d = this._applyZoomConstraints(c);
            if (c !== d) {
              c = d;
              u.width = 1 / c;
              u.x = l.x - u.width / 2;
              u.height = u.width / h;
              u.y = l.y - u.height / 2;
            }
            u = this._applyBoundaryConstraints(u);
            l = u.getCenter();
            this._raiseConstraintsEvent(o);
          }
          if (o) {
            this.panTo(l, true);
            return this.zoomTo(c, null, true);
          }
          this.panTo(this.getCenter(true), true);
          this.zoomTo(this.getZoom(true), null, true);
          var g = this.getBounds();
          var y = this.getZoom();
          if (y === 0 || Math.abs(c / y - 1) < 1e-8) {
            this.zoomTo(c, true);
            return this.panTo(l, o);
          }
          u = u.rotate(-this.getRotation());
          var x = u
            .getTopLeft()
            .times(c)
            .minus(g.getTopLeft().times(y))
            .divide(c - y);
          return this.zoomTo(c, x, o);
        },
        fitBounds: function (n, r) {
          return this._fitBounds(n, { immediately: r, constraints: false });
        },
        fitBoundsWithConstraints: function (n, r) {
          return this._fitBounds(n, { immediately: r, constraints: true });
        },
        fitVertically: function (n) {
          var r = new e.Rect(
            this._contentBounds.x + this._contentBounds.width / 2,
            this._contentBounds.y,
            0,
            this._contentBounds.height
          );
          return this.fitBounds(r, n);
        },
        fitHorizontally: function (n) {
          var r = new e.Rect(
            this._contentBounds.x,
            this._contentBounds.y + this._contentBounds.height / 2,
            this._contentBounds.width,
            0
          );
          return this.fitBounds(r, n);
        },
        getConstrainedBounds: function (n) {
          var r = this.getBounds(n);
          var o = this._applyBoundaryConstraints(r);
          return o;
        },
        panBy: function (n, r) {
          var o = new e.Point(
            this.centerSpringX.target.value,
            this.centerSpringY.target.value
          );
          return this.panTo(o.plus(n), r);
        },
        panTo: function (n, r) {
          if (r) {
            this.centerSpringX.resetTo(n.x);
            this.centerSpringY.resetTo(n.y);
          } else {
            this.centerSpringX.springTo(n.x);
            this.centerSpringY.springTo(n.y);
          }
          if (this.viewer) {
            this.viewer.raiseEvent('pan', { center: n, immediately: r });
          }
          return this;
        },
        zoomBy: function (n, r, o) {
          return this.zoomTo(this.zoomSpring.target.value * n, r, o);
        },
        zoomTo: function (n, r, o) {
          var s = this;
          this.zoomPoint =
            r instanceof e.Point && !isNaN(r.x) && !isNaN(r.y) ? r : null;
          if (o) {
            this._adjustCenterSpringsForZoomPoint(function () {
              s.zoomSpring.resetTo(n);
            });
          } else {
            this.zoomSpring.springTo(n);
          }
          if (this.viewer) {
            this.viewer.raiseEvent('zoom', {
              zoom: n,
              refPoint: r,
              immediately: o,
            });
          }
          return this;
        },
        setRotation: function (n) {
          if (!this.viewer || !this.viewer.drawer.canRotate()) {
            return this;
          } else {
            this.degrees = e.positiveModulo(n, 360);
            this._setContentBounds(
              this.viewer.world.getHomeBounds(),
              this.viewer.world.getContentFactor()
            );
            this.viewer.forceRedraw();
            this.viewer.raiseEvent('rotate', { degrees: n });
            return this;
          }
        },
        getRotation: function () {
          return this.degrees;
        },
        resize: function (n, r) {
          var o = this.getBoundsNoRotate();
          var s = o;
          var a;
          this.containerSize.x = n.x;
          this.containerSize.y = n.y;
          this._updateContainerInnerSize();
          if (r) {
            a = n.x / this.containerSize.x;
            s.width = o.width * a;
            s.height = s.width / this.getAspectRatio();
          }
          if (this.viewer) {
            this.viewer.raiseEvent('resize', {
              newContainerSize: n,
              maintain: r,
            });
          }
          return this.fitBounds(s, true);
        },
        _updateContainerInnerSize: function () {
          this._containerInnerSize = new e.Point(
            Math.max(
              1,
              this.containerSize.x - (this._margins.left + this._margins.right)
            ),
            Math.max(
              1,
              this.containerSize.y - (this._margins.top + this._margins.bottom)
            )
          );
        },
        update: function () {
          var n = this;
          this._adjustCenterSpringsForZoomPoint(function () {
            n.zoomSpring.update();
          });
          this.centerSpringX.update();
          this.centerSpringY.update();
          var r =
            this.centerSpringX.current.value !== this._oldCenterX ||
            this.centerSpringY.current.value !== this._oldCenterY ||
            this.zoomSpring.current.value !== this._oldZoom;
          this._oldCenterX = this.centerSpringX.current.value;
          this._oldCenterY = this.centerSpringY.current.value;
          this._oldZoom = this.zoomSpring.current.value;
          return r;
        },
        _adjustCenterSpringsForZoomPoint: function (n) {
          if (this.zoomPoint) {
            var r = this.pixelFromPoint(this.zoomPoint, true);
            n();
            var o = this.pixelFromPoint(this.zoomPoint, true);
            var s = o.minus(r);
            var a = this.deltaPointsFromPixels(s, true);
            this.centerSpringX.shiftBy(a.x);
            this.centerSpringY.shiftBy(a.y);
            if (this.zoomSpring.isAtTargetValue()) {
              this.zoomPoint = null;
            }
          } else {
            n();
          }
        },
        deltaPixelsFromPointsNoRotate: function (n, r) {
          return n.times(this._containerInnerSize.x * this.getZoom(r));
        },
        deltaPixelsFromPoints: function (n, r) {
          return this.deltaPixelsFromPointsNoRotate(
            n.rotate(this.getRotation()),
            r
          );
        },
        deltaPointsFromPixelsNoRotate: function (n, r) {
          return n.divide(this._containerInnerSize.x * this.getZoom(r));
        },
        deltaPointsFromPixels: function (n, r) {
          return this.deltaPointsFromPixelsNoRotate(n, r).rotate(
            -this.getRotation()
          );
        },
        pixelFromPointNoRotate: function (n, r) {
          return this._pixelFromPointNoRotate(n, this.getBoundsNoRotate(r));
        },
        pixelFromPoint: function (n, r) {
          return this._pixelFromPoint(n, this.getBoundsNoRotate(r));
        },
        _pixelFromPointNoRotate: function (n, r) {
          return n
            .minus(r.getTopLeft())
            .times(this._containerInnerSize.x / r.width)
            .plus(new e.Point(this._margins.left, this._margins.top));
        },
        _pixelFromPoint: function (n, r) {
          return this._pixelFromPointNoRotate(
            n.rotate(this.getRotation(), this.getCenter(true)),
            r
          );
        },
        pointFromPixelNoRotate: function (n, r) {
          var o = this.getBoundsNoRotate(r);
          return n
            .minus(new e.Point(this._margins.left, this._margins.top))
            .divide(this._containerInnerSize.x / o.width)
            .plus(o.getTopLeft());
        },
        pointFromPixel: function (n, r) {
          return this.pointFromPixelNoRotate(n, r).rotate(
            -this.getRotation(),
            this.getCenter(true)
          );
        },
        _viewportToImageDelta: function (n, r) {
          var o = this._contentBoundsNoRotate.width;
          return new e.Point(
            (n * this._contentSizeNoRotate.x) / o,
            (r * this._contentSizeNoRotate.x) / o
          );
        },
        viewportToImageCoordinates: function (n, r) {
          if (n instanceof e.Point) {
            return this.viewportToImageCoordinates(n.x, n.y);
          }
          if (this.viewer) {
            var o = this.viewer.world.getItemCount();
            if (o > 1) {
              e.console.error(
                '[Viewport.viewportToImageCoordinates] is not accurate with multi-image; use TiledImage.viewportToImageCoordinates instead.'
              );
            } else if (o === 1) {
              var s = this.viewer.world.getItemAt(0);
              return s.viewportToImageCoordinates(n, r, true);
            }
          }
          return this._viewportToImageDelta(
            n - this._contentBoundsNoRotate.x,
            r - this._contentBoundsNoRotate.y
          );
        },
        _imageToViewportDelta: function (n, r) {
          var o = this._contentBoundsNoRotate.width;
          return new e.Point(
            (n / this._contentSizeNoRotate.x) * o,
            (r / this._contentSizeNoRotate.x) * o
          );
        },
        imageToViewportCoordinates: function (n, r) {
          if (n instanceof e.Point) {
            return this.imageToViewportCoordinates(n.x, n.y);
          }
          if (this.viewer) {
            var o = this.viewer.world.getItemCount();
            if (o > 1) {
              e.console.error(
                '[Viewport.imageToViewportCoordinates] is not accurate with multi-image; use TiledImage.imageToViewportCoordinates instead.'
              );
            } else if (o === 1) {
              var s = this.viewer.world.getItemAt(0);
              return s.imageToViewportCoordinates(n, r, true);
            }
          }
          var a = this._imageToViewportDelta(n, r);
          a.x += this._contentBoundsNoRotate.x;
          a.y += this._contentBoundsNoRotate.y;
          return a;
        },
        imageToViewportRectangle: function (n, r, o, s) {
          var a = n;
          if (!(a instanceof e.Rect)) {
            a = new e.Rect(n, r, o, s);
          }
          if (this.viewer) {
            var l = this.viewer.world.getItemCount();
            if (l > 1) {
              e.console.error(
                '[Viewport.imageToViewportRectangle] is not accurate with multi-image; use TiledImage.imageToViewportRectangle instead.'
              );
            } else if (l === 1) {
              var u = this.viewer.world.getItemAt(0);
              return u.imageToViewportRectangle(n, r, o, s, true);
            }
          }
          var c = this.imageToViewportCoordinates(a.x, a.y);
          var h = this._imageToViewportDelta(a.width, a.height);
          return new e.Rect(c.x, c.y, h.x, h.y, a.degrees);
        },
        viewportToImageRectangle: function (n, r, o, s) {
          var a = n;
          if (!(a instanceof e.Rect)) {
            a = new e.Rect(n, r, o, s);
          }
          if (this.viewer) {
            var l = this.viewer.world.getItemCount();
            if (l > 1) {
              e.console.error(
                '[Viewport.viewportToImageRectangle] is not accurate with multi-image; use TiledImage.viewportToImageRectangle instead.'
              );
            } else if (l === 1) {
              var u = this.viewer.world.getItemAt(0);
              return u.viewportToImageRectangle(n, r, o, s, true);
            }
          }
          var c = this.viewportToImageCoordinates(a.x, a.y);
          var h = this._viewportToImageDelta(a.width, a.height);
          return new e.Rect(c.x, c.y, h.x, h.y, a.degrees);
        },
        viewerElementToImageCoordinates: function (n) {
          var r = this.pointFromPixel(n, true);
          return this.viewportToImageCoordinates(r);
        },
        imageToViewerElementCoordinates: function (n) {
          var r = this.imageToViewportCoordinates(n);
          return this.pixelFromPoint(r, true);
        },
        windowToImageCoordinates: function (n) {
          e.console.assert(
            this.viewer,
            '[Viewport.windowToImageCoordinates] the viewport must have a viewer.'
          );
          var r = n.minus(e.getElementPosition(this.viewer.element));
          return this.viewerElementToImageCoordinates(r);
        },
        imageToWindowCoordinates: function (n) {
          e.console.assert(
            this.viewer,
            '[Viewport.imageToWindowCoordinates] the viewport must have a viewer.'
          );
          var r = this.imageToViewerElementCoordinates(n);
          return r.plus(e.getElementPosition(this.viewer.element));
        },
        viewerElementToViewportCoordinates: function (n) {
          return this.pointFromPixel(n, true);
        },
        viewportToViewerElementCoordinates: function (n) {
          return this.pixelFromPoint(n, true);
        },
        viewerElementToViewportRectangle: function (n) {
          return e.Rect.fromSummits(
            this.pointFromPixel(n.getTopLeft(), true),
            this.pointFromPixel(n.getTopRight(), true),
            this.pointFromPixel(n.getBottomLeft(), true)
          );
        },
        viewportToViewerElementRectangle: function (n) {
          return e.Rect.fromSummits(
            this.pixelFromPoint(n.getTopLeft(), true),
            this.pixelFromPoint(n.getTopRight(), true),
            this.pixelFromPoint(n.getBottomLeft(), true)
          );
        },
        windowToViewportCoordinates: function (n) {
          e.console.assert(
            this.viewer,
            '[Viewport.windowToViewportCoordinates] the viewport must have a viewer.'
          );
          var r = n.minus(e.getElementPosition(this.viewer.element));
          return this.viewerElementToViewportCoordinates(r);
        },
        viewportToWindowCoordinates: function (n) {
          e.console.assert(
            this.viewer,
            '[Viewport.viewportToWindowCoordinates] the viewport must have a viewer.'
          );
          var r = this.viewportToViewerElementCoordinates(n);
          return r.plus(e.getElementPosition(this.viewer.element));
        },
        viewportToImageZoom: function (n) {
          if (this.viewer) {
            var r = this.viewer.world.getItemCount();
            if (r > 1) {
              e.console.error(
                '[Viewport.viewportToImageZoom] is not accurate with multi-image.'
              );
            } else if (r === 1) {
              var o = this.viewer.world.getItemAt(0);
              return o.viewportToImageZoom(n);
            }
          }
          var s = this._contentSizeNoRotate.x;
          var a = this._containerInnerSize.x;
          var l = this._contentBoundsNoRotate.width;
          var u = (a / s) * l;
          return n * u;
        },
        imageToViewportZoom: function (n) {
          if (this.viewer) {
            var r = this.viewer.world.getItemCount();
            if (r > 1) {
              e.console.error(
                '[Viewport.imageToViewportZoom] is not accurate with multi-image.'
              );
            } else if (r === 1) {
              var o = this.viewer.world.getItemAt(0);
              return o.imageToViewportZoom(n);
            }
          }
          var s = this._contentSizeNoRotate.x;
          var a = this._containerInnerSize.x;
          var l = this._contentBoundsNoRotate.width;
          var u = s / a / l;
          return n * u;
        },
        toggleFlip: function () {
          this.setFlip(!this.getFlip());
          return this;
        },
        getFlip: function () {
          return this.flipped;
        },
        setFlip: function (n) {
          if (this.flipped === n) {
            return this;
          } else {
            this.flipped = n;
            if (this.viewer.navigator) {
              this.viewer.navigator.setFlip(this.getFlip());
            }
            this.viewer.forceRedraw();
            this.viewer.raiseEvent('flip', { flipped: n });
            return this;
          }
        },
      };
    })();
    (function () {
      function n(f, E, A, C, O, D, N, B, Z) {
        var Y = N.getBoundingBox().getTopLeft();
        var U = N.getBoundingBox().getBottomRight();
        if (f.viewer) {
          f.viewer.raiseEvent('update-level', {
            tiledImage: f,
            havedrawn: E,
            level: C,
            opacity: O,
            visibility: D,
            drawArea: N,
            topleft: Y,
            bottomright: U,
            currenttime: B,
            best: Z,
          });
        }
        y(f.coverage, C);
        y(f.loadingCoverage, C);
        var K = f._getCornerTiles(C, Y, U);
        var Q = K.topLeft;
        var le = K.bottomRight;
        var re = f.source.getNumTiles(C);
        var se = f.viewport.pixelFromPoint(f.viewport.getCenter());
        if (f.getFlip()) {
          le.x += 1;
          if (!f.wrapHorizontal) {
            le.x = Math.min(le.x, re.x - 1);
          }
        }
        for (var fe = Q.x; fe <= le.x; fe++) {
          for (var me = Q.y; me <= le.y; me++) {
            var q;
            if (f.getFlip()) {
              var Le = (re.x + (fe % re.x)) % re.x;
              q = fe + re.x - Le - Le - 1;
            } else {
              q = fe;
            }
            if (N.intersection(f.getTileBounds(C, q, me)) !== null) {
              Z = r(f, A, E, q, me, C, O, D, se, re, B, Z);
            }
          }
        }
        return Z;
      }
      function r(f, E, A, C, O, D, N, B, Z, Y, U, K) {
        var Q = o(
          C,
          O,
          D,
          f,
          f.source,
          f.tilesMatrix,
          U,
          Y,
          f._worldWidthCurrent,
          f._worldHeightCurrent
        );
        var le = A;
        if (f.viewer) {
          f.viewer.raiseEvent('update-tile', { tiledImage: f, tile: Q });
        }
        g(f.coverage, D, C, O, false);
        var re = Q.loaded || Q.loading || d(f.loadingCoverage, D, C, O);
        g(f.loadingCoverage, D, C, O, re);
        if (
          !Q.exists ||
          (E &&
            !le &&
            (d(f.coverage, D, C, O)
              ? g(f.coverage, D, C, O, true)
              : (le = true)),
          !le)
        ) {
          return K;
        }
        u(Q, f.source.tileOverlap, f.viewport, Z, B, f);
        if (!Q.loaded) {
          if (Q.context2D) {
            l(f, Q);
          } else {
            var se = f._tileCache.getImageRecord(Q.cacheKey);
            if (se) {
              var fe = se.getImage();
              l(f, Q, fe);
            }
          }
        }
        if (Q.loaded) {
          var me = c(f, Q, C, O, D, N, U);
          if (me) {
            f._needsDraw = true;
          }
        } else if (Q.loading) {
          f._tilesLoading++;
        } else if (!re) {
          K = x(K, Q);
        }
        return K;
      }
      function o(f, E, A, C, O, D, N, B, Z, Y) {
        var U;
        var K;
        var Q;
        var le;
        var re;
        var se;
        var fe;
        var me;
        if (!D[A]) {
          D[A] = {};
        }
        if (!D[A][f]) {
          D[A][f] = {};
        }
        if (!D[A][f][E] || !D[A][f][E].flipped != !C.flipped) {
          U = (B.x + (f % B.x)) % B.x;
          K = (B.y + (E % B.y)) % B.y;
          Q = C.getTileBounds(A, f, E);
          le = O.getTileBounds(A, U, K, true);
          re = O.tileExists(A, U, K);
          se = O.getTileUrl(A, U, K);
          if (C.loadTilesWithAjax) {
            fe = O.getTileAjaxHeaders(A, U, K);
            if (e.isPlainObject(C.ajaxHeaders)) {
              fe = e.extend({}, C.ajaxHeaders, fe);
            }
          } else {
            fe = null;
          }
          me = O.getContext2D ? O.getContext2D(A, U, K) : void 0;
          q = new e.Tile(A, f, E, Q, re, se, me, C.loadTilesWithAjax, fe, le);
          if (C.getFlip()) {
            if (U === 0) {
              q.isRightMost = true;
            }
          } else if (U === B.x - 1) {
            q.isRightMost = true;
          }
          if (K === B.y - 1) {
            q.isBottomMost = true;
          }
          q.flipped = C.flipped;
          D[A][f][E] = q;
        }
        var q = D[A][f][E];
        q.lastTouchTime = N;
        return q;
      }
      function s(f, E, A) {
        E.loading = true;
        f._imageLoader.addJob({
          src: E.url,
          loadWithAjax: E.loadWithAjax,
          ajaxHeaders: E.ajaxHeaders,
          crossOriginPolicy: f.crossOriginPolicy,
          ajaxWithCredentials: f.ajaxWithCredentials,
          callback: function (C, O, D) {
            a(f, E, A, C, O, D);
          },
          abort: function () {
            E.loading = false;
          },
        });
      }
      function a(f, E, A, C, O, D) {
        if (!C) {
          e.console.log('Tile %s failed to load: %s - error: %s', E, E.url, O);
          f.viewer.raiseEvent('tile-load-failed', {
            tile: E,
            tiledImage: f,
            time: A,
            message: O,
            tileRequest: D,
          });
          E.loading = false;
          E.exists = false;
          return;
        }
        if (A < f.lastResetTime) {
          e.console.log('Ignoring tile %s loaded before reset: %s', E, E.url);
          E.loading = false;
          return;
        }
        var N = function () {
          var B = f.source.getClosestLevel();
          l(f, E, C, B, D);
        };
        if (f._midDraw) {
          window.setTimeout(N, 1);
        } else {
          N();
        }
      }
      function l(f, E, A, C, O) {
        function N() {
          D++;
          return B;
        }
        function B() {
          D--;
          if (D === 0) {
            E.loading = false;
            E.loaded = true;
            if (!E.context2D) {
              f._tileCache.cacheTile({
                image: A,
                tile: E,
                cutoff: C,
                tiledImage: f,
              });
            }
            f._needsDraw = true;
          }
        }
        var D = 0;
        f.viewer.raiseEvent('tile-loaded', {
          tile: E,
          tiledImage: f,
          tileRequest: O,
          image: A,
          getCompletionCallback: N,
        });
        N()();
      }
      function u(f, E, A, C, O, D) {
        var N = f.bounds.getTopLeft();
        N.x *= D._scaleSpring.current.value;
        N.y *= D._scaleSpring.current.value;
        N.x += D._xSpring.current.value;
        N.y += D._ySpring.current.value;
        var B = f.bounds.getSize();
        B.x *= D._scaleSpring.current.value;
        B.y *= D._scaleSpring.current.value;
        var Z = A.pixelFromPointNoRotate(N, true);
        var Y = A.pixelFromPointNoRotate(N, false);
        var U = A.deltaPixelsFromPointsNoRotate(B, true);
        var K = A.deltaPixelsFromPointsNoRotate(B, false);
        var Q = Y.plus(K.divide(2));
        var le = C.squaredDistanceTo(Q);
        if (!E) {
          U = U.plus(new e.Point(1, 1));
        }
        if (f.isRightMost && D.wrapHorizontal) {
          U.x += 0.75;
        }
        if (f.isBottomMost && D.wrapVertical) {
          U.y += 0.75;
        }
        f.position = Z;
        f.size = U;
        f.squaredDistance = le;
        f.visibility = O;
      }
      function c(f, E, A, C, O, D, N) {
        var B = 1e3 * f.blendTime;
        if (!E.blendStart) {
          E.blendStart = N;
        }
        var Z = N - E.blendStart;
        var Y = B ? Math.min(1, Z / B) : 1;
        if (f.alwaysBlend) {
          Y *= D;
        }
        E.opacity = Y;
        f.lastDrawn.push(E);
        if (Y === 1) {
          g(f.coverage, O, A, C, true);
          f._hasOpaqueTile = true;
        } else if (Z < B) {
          return true;
        }
        return false;
      }
      function h(f, E, A, C) {
        var O;
        var D;
        var N;
        var B;
        if (!f[E]) {
          return false;
        }
        if (A === void 0 || C === void 0) {
          O = f[E];
          for (N in O) {
            if (Object.prototype.hasOwnProperty.call(O, N)) {
              D = O[N];
              for (B in D) {
                if (Object.prototype.hasOwnProperty.call(D, B) && !D[B]) {
                  return false;
                }
              }
            }
          }
          return true;
        }
        return (
          f[E][A] === void 0 || f[E][A][C] === void 0 || f[E][A][C] === true
        );
      }
      function d(f, E, A, C) {
        if (A === void 0 || C === void 0) {
          return h(f, E + 1);
        } else {
          return (
            h(f, E + 1, 2 * A, 2 * C) &&
            h(f, E + 1, 2 * A, 2 * C + 1) &&
            h(f, E + 1, 2 * A + 1, 2 * C) &&
            h(f, E + 1, 2 * A + 1, 2 * C + 1)
          );
        }
      }
      function g(f, E, A, C, O) {
        if (!f[E]) {
          e.console.warn(
            "Setting coverage for a tile before its level's coverage has been reset: %s",
            E
          );
          return;
        }
        if (!f[E][A]) {
          f[E][A] = {};
        }
        f[E][A][C] = O;
      }
      function y(f, E) {
        f[E] = {};
      }
      function x(f, E) {
        if (
          !f ||
          E.visibility > f.visibility ||
          (E.visibility === f.visibility &&
            E.squaredDistance < f.squaredDistance)
        ) {
          return E;
        } else {
          return f;
        }
      }
      function b(f, E) {
        if (f.opacity !== 0 && (E.length !== 0 || !!f.placeholderFillStyle)) {
          var A = E[0];
          var C;
          if (A) {
            C =
              f.opacity < 1 ||
              (f.compositeOperation &&
                f.compositeOperation !== 'source-over') ||
              (!f._isBottomItem() && A._hasTransparencyChannel());
          }
          var O;
          var D;
          var N = f.viewport.getZoom(true);
          var B = f.viewportToImageZoom(N);
          if (
            E.length > 1 &&
            B > f.smoothTileEdgesMinZoom &&
            !f.iOSDevice &&
            f.getRotation(true) % 360 == 0 &&
            e.supportsCanvas
          ) {
            C = true;
            O = A.getScaleForEdgeSmoothing();
            D = A.getTranslationForEdgeSmoothing(
              O,
              f._drawer.getCanvasSize(false),
              f._drawer.getCanvasSize(true)
            );
          }
          var Z;
          if (C) {
            if (!O) {
              Z = f.viewport
                .viewportToViewerElementRectangle(f.getClippedBounds(true))
                .getIntegerBoundingBox();
              if (
                f._drawer.viewer.viewport.getFlip() &&
                (f.viewport.degrees !== 0 || f.getRotation(true) % 360 != 0)
              ) {
                Z.x = f._drawer.viewer.container.clientWidth - (Z.x + Z.width);
              }
              Z = Z.times(e.pixelDensityRatio);
            }
            f._drawer._clear(true, Z);
          }
          if (!O) {
            if (f.viewport.degrees !== 0) {
              f._drawer._offsetForRotation({
                degrees: f.viewport.degrees,
                useSketch: C,
              });
            }
            if (f.getRotation(true) % 360 != 0) {
              f._drawer._offsetForRotation({
                degrees: f.getRotation(true),
                point: f.viewport.pixelFromPointNoRotate(
                  f._getRotationPoint(true),
                  true
                ),
                useSketch: C,
              });
            }
            if (
              f.viewport.degrees === 0 &&
              f.getRotation(true) % 360 == 0 &&
              f._drawer.viewer.viewport.getFlip()
            ) {
              f._drawer._flip();
            }
          }
          var Y = false;
          if (f._clip) {
            f._drawer.saveContext(C);
            var U = f.imageToViewportRectangle(f._clip, true);
            U = U.rotate(-f.getRotation(true), f._getRotationPoint(true));
            var K = f._drawer.viewportToDrawerRectangle(U);
            if (O) {
              K = K.times(O);
            }
            if (D) {
              K = K.translate(D);
            }
            f._drawer.setClip(K, C);
            Y = true;
          }
          if (f._croppingPolygons) {
            f._drawer.saveContext(C);
            try {
              var Q = f._croppingPolygons.map(function (fe) {
                return fe.map(function (me) {
                  var q = f
                    .imageToViewportCoordinates(me.x, me.y, true)
                    .rotate(-f.getRotation(true), f._getRotationPoint(true));
                  var Le = f._drawer.viewportCoordToDrawerCoord(q);
                  if (O) {
                    Le = Le.times(O);
                  }
                  return Le;
                });
              });
              f._drawer.clipWithPolygons(Q, C);
            } catch (fe) {
              e.console.error(fe);
            }
            Y = true;
          }
          if (f.placeholderFillStyle && f._hasOpaqueTile === false) {
            var le = f._drawer.viewportToDrawerRectangle(f.getBounds(true));
            if (O) {
              le = le.times(O);
            }
            if (D) {
              le = le.translate(D);
            }
            var re = null;
            if (typeof f.placeholderFillStyle == 'function') {
              re = f.placeholderFillStyle(f, f._drawer.context);
            } else {
              re = f.placeholderFillStyle;
            }
            f._drawer.drawRectangle(le, re, C);
          }
          for (var se = E.length - 1; se >= 0; se--) {
            A = E[se];
            f._drawer.drawTile(A, f._drawingHandler, C, O, D);
            A.beingDrawn = true;
            if (f.viewer) {
              f.viewer.raiseEvent('tile-drawn', { tiledImage: f, tile: A });
            }
          }
          if (Y) {
            f._drawer.restoreContext(C);
          }
          if (!O) {
            if (f.getRotation(true) % 360 != 0) {
              f._drawer._restoreRotationChanges(C);
            }
            if (f.viewport.degrees !== 0) {
              f._drawer._restoreRotationChanges(C);
            }
          }
          if (C) {
            if (O) {
              if (f.viewport.degrees !== 0) {
                f._drawer._offsetForRotation({
                  degrees: f.viewport.degrees,
                  useSketch: false,
                });
              }
              if (f.getRotation(true) % 360 != 0) {
                f._drawer._offsetForRotation({
                  degrees: f.getRotation(true),
                  point: f.viewport.pixelFromPointNoRotate(
                    f._getRotationPoint(true),
                    true
                  ),
                  useSketch: false,
                });
              }
            }
            f._drawer.blendSketch({
              opacity: f.opacity,
              scale: O,
              translate: D,
              compositeOperation: f.compositeOperation,
              bounds: Z,
            });
            if (O) {
              if (f.getRotation(true) % 360 != 0) {
                f._drawer._restoreRotationChanges(false);
              }
              if (f.viewport.degrees !== 0) {
                f._drawer._restoreRotationChanges(false);
              }
            }
          }
          if (!O) {
            if (
              f.viewport.degrees === 0 &&
              f.getRotation(true) % 360 == 0 &&
              f._drawer.viewer.viewport.getFlip()
            ) {
              f._drawer._flip();
            }
          }
          T(f, E);
        }
      }
      function T(f, E) {
        if (f.debugMode) {
          for (var A = E.length - 1; A >= 0; A--) {
            var C = E[A];
            try {
              f._drawer.drawDebugInfo(C, E.length, A, f);
            } catch (O) {
              e.console.error(O);
            }
          }
        }
      }
      var e = t;
      e.TiledImage = function (f) {
        var E = this;
        e.console.assert(
          f.tileCache,
          '[TiledImage] options.tileCache is required'
        );
        e.console.assert(f.drawer, '[TiledImage] options.drawer is required');
        e.console.assert(f.viewer, '[TiledImage] options.viewer is required');
        e.console.assert(
          f.imageLoader,
          '[TiledImage] options.imageLoader is required'
        );
        e.console.assert(f.source, '[TiledImage] options.source is required');
        e.console.assert(
          !f.clip || f.clip instanceof e.Rect,
          '[TiledImage] options.clip must be an OpenSeadragon.Rect if present'
        );
        e.EventSource.call(this);
        this._tileCache = f.tileCache;
        delete f.tileCache;
        this._drawer = f.drawer;
        delete f.drawer;
        this._imageLoader = f.imageLoader;
        delete f.imageLoader;
        if (f.clip instanceof e.Rect) {
          this._clip = f.clip.clone();
        }
        delete f.clip;
        var A = f.x || 0;
        delete f.x;
        var C = f.y || 0;
        delete f.y;
        this.normHeight = f.source.dimensions.y / f.source.dimensions.x;
        this.contentAspectX = f.source.dimensions.x / f.source.dimensions.y;
        var O = 1;
        if (f.width) {
          O = f.width;
          delete f.width;
          if (f.height) {
            e.console.error(
              'specifying both width and height to a tiledImage is not supported'
            );
            delete f.height;
          }
        } else if (f.height) {
          O = f.height / this.normHeight;
          delete f.height;
        }
        var D = f.fitBounds;
        delete f.fitBounds;
        var N = f.fitBoundsPlacement || t.Placement.CENTER;
        delete f.fitBoundsPlacement;
        var B = f.degrees || 0;
        delete f.degrees;
        e.extend(
          true,
          this,
          {
            viewer: null,
            tilesMatrix: {},
            coverage: {},
            loadingCoverage: {},
            lastDrawn: [],
            lastResetTime: 0,
            _midDraw: false,
            _needsDraw: true,
            _hasOpaqueTile: false,
            _tilesLoading: 0,
            springStiffness: e.DEFAULT_SETTINGS.springStiffness,
            animationTime: e.DEFAULT_SETTINGS.animationTime,
            minZoomImageRatio: e.DEFAULT_SETTINGS.minZoomImageRatio,
            wrapHorizontal: e.DEFAULT_SETTINGS.wrapHorizontal,
            wrapVertical: e.DEFAULT_SETTINGS.wrapVertical,
            immediateRender: e.DEFAULT_SETTINGS.immediateRender,
            blendTime: e.DEFAULT_SETTINGS.blendTime,
            alwaysBlend: e.DEFAULT_SETTINGS.alwaysBlend,
            minPixelRatio: e.DEFAULT_SETTINGS.minPixelRatio,
            smoothTileEdgesMinZoom: e.DEFAULT_SETTINGS.smoothTileEdgesMinZoom,
            iOSDevice: e.DEFAULT_SETTINGS.iOSDevice,
            debugMode: e.DEFAULT_SETTINGS.debugMode,
            crossOriginPolicy: e.DEFAULT_SETTINGS.crossOriginPolicy,
            ajaxWithCredentials: e.DEFAULT_SETTINGS.ajaxWithCredentials,
            placeholderFillStyle: e.DEFAULT_SETTINGS.placeholderFillStyle,
            opacity: e.DEFAULT_SETTINGS.opacity,
            preload: e.DEFAULT_SETTINGS.preload,
            compositeOperation: e.DEFAULT_SETTINGS.compositeOperation,
          },
          f
        );
        this._preload = this.preload;
        delete this.preload;
        this._fullyLoaded = false;
        this._xSpring = new e.Spring({
          initial: A,
          springStiffness: this.springStiffness,
          animationTime: this.animationTime,
        });
        this._ySpring = new e.Spring({
          initial: C,
          springStiffness: this.springStiffness,
          animationTime: this.animationTime,
        });
        this._scaleSpring = new e.Spring({
          initial: O,
          springStiffness: this.springStiffness,
          animationTime: this.animationTime,
        });
        this._degreesSpring = new e.Spring({
          initial: B,
          springStiffness: this.springStiffness,
          animationTime: this.animationTime,
        });
        this._updateForScale();
        if (D) {
          this.fitBounds(D, N, true);
        }
        this._drawingHandler = function (Z) {
          E.viewer.raiseEvent('tile-drawing', e.extend({ tiledImage: E }, Z));
        };
      };
      e.extend(e.TiledImage.prototype, e.EventSource.prototype, {
        needsDraw: function () {
          return this._needsDraw;
        },
        getFullyLoaded: function () {
          return this._fullyLoaded;
        },
        _setFullyLoaded: function (f) {
          if (f !== this._fullyLoaded) {
            this._fullyLoaded = f;
            this.raiseEvent('fully-loaded-change', {
              fullyLoaded: this._fullyLoaded,
            });
          }
        },
        reset: function () {
          this._tileCache.clearTilesFor(this);
          this.lastResetTime = e.now();
          this._needsDraw = true;
        },
        update: function () {
          var f = this._xSpring.update();
          var E = this._ySpring.update();
          var A = this._scaleSpring.update();
          var C = this._degreesSpring.update();
          if (f || E || A || C) {
            this._updateForScale();
            this._needsDraw = true;
            return true;
          } else {
            return false;
          }
        },
        draw: function () {
          if (this.opacity !== 0 || this._preload) {
            this._midDraw = true;
            this._updateViewport();
            this._midDraw = false;
          } else {
            this._needsDraw = false;
          }
        },
        destroy: function () {
          this.reset();
          if (this.source.destroy) {
            this.source.destroy();
          }
        },
        getBounds: function (f) {
          return this.getBoundsNoRotate(f).rotate(
            this.getRotation(f),
            this._getRotationPoint(f)
          );
        },
        getBoundsNoRotate: function (f) {
          if (f) {
            return new e.Rect(
              this._xSpring.current.value,
              this._ySpring.current.value,
              this._worldWidthCurrent,
              this._worldHeightCurrent
            );
          } else {
            return new e.Rect(
              this._xSpring.target.value,
              this._ySpring.target.value,
              this._worldWidthTarget,
              this._worldHeightTarget
            );
          }
        },
        getWorldBounds: function () {
          e.console.error(
            '[TiledImage.getWorldBounds] is deprecated; use TiledImage.getBounds instead'
          );
          return this.getBounds();
        },
        getClippedBounds: function (f) {
          var E = this.getBoundsNoRotate(f);
          if (this._clip) {
            var A = f ? this._worldWidthCurrent : this._worldWidthTarget;
            var C = A / this.source.dimensions.x;
            var O = this._clip.times(C);
            E = new e.Rect(E.x + O.x, E.y + O.y, O.width, O.height);
          }
          return E.rotate(this.getRotation(f), this._getRotationPoint(f));
        },
        getTileBounds: function (f, E, A) {
          var C = this.source.getNumTiles(f);
          var O = (C.x + (E % C.x)) % C.x;
          var D = (C.y + (A % C.y)) % C.y;
          var N = this.source.getTileBounds(f, O, D);
          if (this.getFlip()) {
            N.x = 1 - N.x - N.width;
          }
          N.x += (E - O) / C.x;
          N.y +=
            (this._worldHeightCurrent / this._worldWidthCurrent) *
            ((A - D) / C.y);
          return N;
        },
        getContentSize: function () {
          return new e.Point(
            this.source.dimensions.x,
            this.source.dimensions.y
          );
        },
        getSizeInWindowCoordinates: function () {
          var f = this.imageToWindowCoordinates(new e.Point(0, 0));
          var E = this.imageToWindowCoordinates(this.getContentSize());
          return new e.Point(E.x - f.x, E.y - f.y);
        },
        _viewportToImageDelta: function (f, E, A) {
          var C = A
            ? this._scaleSpring.current.value
            : this._scaleSpring.target.value;
          return new e.Point(
            f * (this.source.dimensions.x / C),
            E * ((this.source.dimensions.y * this.contentAspectX) / C)
          );
        },
        viewportToImageCoordinates: function (f, E, A) {
          if (f instanceof e.Point) {
            A = E;
            C = f;
          } else {
            C = new e.Point(f, E);
          }
          var C = C.rotate(-this.getRotation(A), this._getRotationPoint(A));
          if (A) {
            return this._viewportToImageDelta(
              C.x - this._xSpring.current.value,
              C.y - this._ySpring.current.value
            );
          } else {
            return this._viewportToImageDelta(
              C.x - this._xSpring.target.value,
              C.y - this._ySpring.target.value
            );
          }
        },
        _imageToViewportDelta: function (f, E, A) {
          var C = A
            ? this._scaleSpring.current.value
            : this._scaleSpring.target.value;
          return new e.Point(
            (f / this.source.dimensions.x) * C,
            (E / this.source.dimensions.y / this.contentAspectX) * C
          );
        },
        imageToViewportCoordinates: function (f, E, A) {
          if (f instanceof e.Point) {
            A = E;
            E = f.y;
            f = f.x;
          }
          var C = this._imageToViewportDelta(f, E);
          if (A) {
            C.x += this._xSpring.current.value;
            C.y += this._ySpring.current.value;
          } else {
            C.x += this._xSpring.target.value;
            C.y += this._ySpring.target.value;
          }
          return C.rotate(this.getRotation(A), this._getRotationPoint(A));
        },
        imageToViewportRectangle: function (f, E, A, C, O) {
          var D = f;
          if (D instanceof e.Rect) {
            O = E;
          } else {
            D = new e.Rect(f, E, A, C);
          }
          var N = this.imageToViewportCoordinates(D.getTopLeft(), O);
          var B = this._imageToViewportDelta(D.width, D.height, O);
          return new e.Rect(
            N.x,
            N.y,
            B.x,
            B.y,
            D.degrees + this.getRotation(O)
          );
        },
        viewportToImageRectangle: function (f, E, A, C, O) {
          var D = f;
          if (f instanceof e.Rect) {
            O = E;
          } else {
            D = new e.Rect(f, E, A, C);
          }
          var N = this.viewportToImageCoordinates(D.getTopLeft(), O);
          var B = this._viewportToImageDelta(D.width, D.height, O);
          return new e.Rect(
            N.x,
            N.y,
            B.x,
            B.y,
            D.degrees - this.getRotation(O)
          );
        },
        viewerElementToImageCoordinates: function (f) {
          var E = this.viewport.pointFromPixel(f, true);
          return this.viewportToImageCoordinates(E);
        },
        imageToViewerElementCoordinates: function (f) {
          var E = this.imageToViewportCoordinates(f);
          return this.viewport.pixelFromPoint(E, true);
        },
        windowToImageCoordinates: function (f) {
          var E = f.minus(t.getElementPosition(this.viewer.element));
          return this.viewerElementToImageCoordinates(E);
        },
        imageToWindowCoordinates: function (f) {
          var E = this.imageToViewerElementCoordinates(f);
          return E.plus(t.getElementPosition(this.viewer.element));
        },
        _viewportToTiledImageRectangle: function (f) {
          var E = this._scaleSpring.current.value;
          f = f.rotate(-this.getRotation(true), this._getRotationPoint(true));
          return new e.Rect(
            (f.x - this._xSpring.current.value) / E,
            (f.y - this._ySpring.current.value) / E,
            f.width / E,
            f.height / E,
            f.degrees
          );
        },
        viewportToImageZoom: function (f) {
          var E =
            (this._scaleSpring.current.value *
              this.viewport._containerInnerSize.x) /
            this.source.dimensions.x;
          return E * f;
        },
        imageToViewportZoom: function (f) {
          var E =
            (this._scaleSpring.current.value *
              this.viewport._containerInnerSize.x) /
            this.source.dimensions.x;
          return f / E;
        },
        setPosition: function (f, E) {
          var A =
            this._xSpring.target.value === f.x &&
            this._ySpring.target.value === f.y;
          if (E) {
            if (
              A &&
              this._xSpring.current.value === f.x &&
              this._ySpring.current.value === f.y
            ) {
              return;
            }
            this._xSpring.resetTo(f.x);
            this._ySpring.resetTo(f.y);
            this._needsDraw = true;
          } else {
            if (A) {
              return;
            }
            this._xSpring.springTo(f.x);
            this._ySpring.springTo(f.y);
            this._needsDraw = true;
          }
          if (!A) {
            this._raiseBoundsChange();
          }
        },
        setWidth: function (f, E) {
          this._setScale(f, E);
        },
        setHeight: function (f, E) {
          this._setScale(f / this.normHeight, E);
        },
        setCroppingPolygons: function (f) {
          var E = function (C) {
            return (
              C instanceof e.Point ||
              (typeof C.x == 'number' && typeof C.y == 'number')
            );
          };
          var A = function (C) {
            return C.map(function (O) {
              try {
                if (E(O)) {
                  return { x: O.x, y: O.y };
                }
                throw new Error();
              } catch {
                throw new Error(
                  'A Provided cropping polygon point is not supported'
                );
              }
            });
          };
          try {
            if (!e.isArray(f)) {
              throw new Error('Provided cropping polygon is not an array');
            }
            this._croppingPolygons = f.map(function (C) {
              return A(C);
            });
          } catch (C) {
            e.console.error(
              '[TiledImage.setCroppingPolygons] Cropping polygon format not supported'
            );
            e.console.error(C);
            this._croppingPolygons = null;
          }
        },
        resetCroppingPolygons: function () {
          this._croppingPolygons = null;
        },
        fitBounds: function (f, E, A) {
          E = E || e.Placement.CENTER;
          var C = e.Placement.properties[E];
          var O = this.contentAspectX;
          var D = 0;
          var N = 0;
          var B = 1;
          var Z = 1;
          if (this._clip) {
            O = this._clip.getAspectRatio();
            B = this._clip.width / this.source.dimensions.x;
            Z = this._clip.height / this.source.dimensions.y;
            if (f.getAspectRatio() > O) {
              D = (this._clip.x / this._clip.height) * f.height;
              N = (this._clip.y / this._clip.height) * f.height;
            } else {
              D = (this._clip.x / this._clip.width) * f.width;
              N = (this._clip.y / this._clip.width) * f.width;
            }
          }
          if (f.getAspectRatio() > O) {
            var Y = f.height / Z;
            var U = 0;
            if (C.isHorizontallyCentered) {
              U = (f.width - f.height * O) / 2;
            } else if (C.isRight) {
              U = f.width - f.height * O;
            }
            this.setPosition(new e.Point(f.x - D + U, f.y - N), A);
            this.setHeight(Y, A);
          } else {
            var K = f.width / B;
            var Q = 0;
            if (C.isVerticallyCentered) {
              Q = (f.height - f.width / O) / 2;
            } else if (C.isBottom) {
              Q = f.height - f.width / O;
            }
            this.setPosition(new e.Point(f.x - D, f.y - N + Q), A);
            this.setWidth(K, A);
          }
        },
        getClip: function () {
          if (this._clip) {
            return this._clip.clone();
          } else {
            return null;
          }
        },
        setClip: function (f) {
          e.console.assert(
            !f || f instanceof e.Rect,
            '[TiledImage.setClip] newClip must be an OpenSeadragon.Rect or null'
          );
          if (f instanceof e.Rect) {
            this._clip = f.clone();
          } else {
            this._clip = null;
          }
          this._needsDraw = true;
          this.raiseEvent('clip-change');
        },
        getFlip: function () {
          return !!this.flipped;
        },
        setFlip: function (f) {
          this.flipped = !!f;
          this._needsDraw = true;
          this._raiseBoundsChange();
        },
        getOpacity: function () {
          return this.opacity;
        },
        setOpacity: function (f) {
          if (f !== this.opacity) {
            this.opacity = f;
            this._needsDraw = true;
            this.raiseEvent('opacity-change', { opacity: this.opacity });
          }
        },
        getPreload: function () {
          return this._preload;
        },
        setPreload: function (f) {
          this._preload = !!f;
          this._needsDraw = true;
        },
        getRotation: function (f) {
          if (f) {
            return this._degreesSpring.current.value;
          } else {
            return this._degreesSpring.target.value;
          }
        },
        setRotation: function (f, E) {
          if (
            this._degreesSpring.target.value !== f ||
            !this._degreesSpring.isAtTargetValue()
          ) {
            if (E) {
              this._degreesSpring.resetTo(f);
            } else {
              this._degreesSpring.springTo(f);
            }
            this._needsDraw = true;
            this._raiseBoundsChange();
          }
        },
        _getRotationPoint: function (f) {
          return this.getBoundsNoRotate(f).getCenter();
        },
        getCompositeOperation: function () {
          return this.compositeOperation;
        },
        setCompositeOperation: function (f) {
          if (f !== this.compositeOperation) {
            this.compositeOperation = f;
            this._needsDraw = true;
            this.raiseEvent('composite-operation-change', {
              compositeOperation: this.compositeOperation,
            });
          }
        },
        _setScale: function (f, E) {
          var A = this._scaleSpring.target.value === f;
          if (E) {
            if (A && this._scaleSpring.current.value === f) {
              return;
            }
            this._scaleSpring.resetTo(f);
            this._updateForScale();
            this._needsDraw = true;
          } else {
            if (A) {
              return;
            }
            this._scaleSpring.springTo(f);
            this._updateForScale();
            this._needsDraw = true;
          }
          if (!A) {
            this._raiseBoundsChange();
          }
        },
        _updateForScale: function () {
          this._worldWidthTarget = this._scaleSpring.target.value;
          this._worldHeightTarget =
            this.normHeight * this._scaleSpring.target.value;
          this._worldWidthCurrent = this._scaleSpring.current.value;
          this._worldHeightCurrent =
            this.normHeight * this._scaleSpring.current.value;
        },
        _raiseBoundsChange: function () {
          this.raiseEvent('bounds-change');
        },
        _isBottomItem: function () {
          return this.viewer.world.getItemAt(0) === this;
        },
        _getLevelsInterval: function () {
          var f = Math.max(
            this.source.minLevel,
            Math.floor(Math.log(this.minZoomImageRatio) / Math.log(2))
          );
          var E =
            this.viewport.deltaPixelsFromPointsNoRotate(
              this.source.getPixelRatio(0),
              true
            ).x * this._scaleSpring.current.value;
          var A = Math.min(
            Math.abs(this.source.maxLevel),
            Math.abs(Math.floor(Math.log(E / this.minPixelRatio) / Math.log(2)))
          );
          A = Math.max(A, this.source.minLevel || 0);
          f = Math.min(f, A);
          return { lowestLevel: f, highestLevel: A };
        },
        _updateViewport: function () {
          this._needsDraw = false;
          this._tilesLoading = 0;
          for (this.loadingCoverage = {}; this.lastDrawn.length > 0; ) {
            var f = this.lastDrawn.pop();
            f.beingDrawn = false;
          }
          var E = this.viewport;
          var A = this._viewportToTiledImageRectangle(
            E.getBoundsWithMargins(true)
          );
          if (!this.wrapHorizontal && !this.wrapVertical) {
            var C = this._viewportToTiledImageRectangle(
              this.getClippedBounds(true)
            );
            A = A.intersection(C);
            if (A === null) {
              return;
            }
          }
          var O = this._getLevelsInterval();
          var D = O.lowestLevel;
          var N = O.highestLevel;
          var B = null;
          var Z = false;
          var Y = e.now();
          for (var U = N; U >= D; U--) {
            var K = false;
            var Q =
              E.deltaPixelsFromPointsNoRotate(
                this.source.getPixelRatio(U),
                true
              ).x * this._scaleSpring.current.value;
            if (U === D || (!Z && Q >= this.minPixelRatio)) {
              K = true;
              Z = true;
            } else if (!Z) {
              continue;
            }
            var le =
              E.deltaPixelsFromPointsNoRotate(
                this.source.getPixelRatio(U),
                false
              ).x * this._scaleSpring.current.value;
            var re =
              E.deltaPixelsFromPointsNoRotate(
                this.source.getPixelRatio(
                  Math.max(this.source.getClosestLevel(), 0)
                ),
                false
              ).x * this._scaleSpring.current.value;
            var se = this.immediateRender ? 1 : re;
            var fe = Math.min(1, (Q - 0.5) / 0.5);
            var me = se / Math.abs(se - le);
            B = n(this, Z, K, U, fe, me, A, Y, B);
            if (h(this.coverage, U)) {
              break;
            }
          }
          b(this, this.lastDrawn);
          if (B && !B.context2D) {
            s(this, B, Y);
            this._needsDraw = true;
            this._setFullyLoaded(false);
          } else {
            this._setFullyLoaded(this._tilesLoading === 0);
          }
        },
        _getCornerTiles: function (f, E, A) {
          var C;
          var O;
          if (this.wrapHorizontal) {
            C = e.positiveModulo(E.x, 1);
            O = e.positiveModulo(A.x, 1);
          } else {
            C = Math.max(0, E.x);
            O = Math.min(1, A.x);
          }
          var D;
          var N;
          var B = 1 / this.source.aspectRatio;
          if (this.wrapVertical) {
            D = e.positiveModulo(E.y, B);
            N = e.positiveModulo(A.y, B);
          } else {
            D = Math.max(0, E.y);
            N = Math.min(B, A.y);
          }
          var Z = this.source.getTileAtPoint(f, new e.Point(C, D));
          var Y = this.source.getTileAtPoint(f, new e.Point(O, N));
          var U = this.source.getNumTiles(f);
          if (this.wrapHorizontal) {
            Z.x += U.x * Math.floor(E.x);
            Y.x += U.x * Math.floor(A.x);
          }
          if (this.wrapVertical) {
            Z.y += U.y * Math.floor(E.y / B);
            Y.y += U.y * Math.floor(A.y / B);
          }
          return { topLeft: Z, bottomRight: Y };
        },
      });
    })();
    (function () {
      var e = t;
      var n = function (o) {
        e.console.assert(o, '[TileCache.cacheTile] options is required');
        e.console.assert(
          o.tile,
          '[TileCache.cacheTile] options.tile is required'
        );
        e.console.assert(
          o.tiledImage,
          '[TileCache.cacheTile] options.tiledImage is required'
        );
        this.tile = o.tile;
        this.tiledImage = o.tiledImage;
      };
      var r = function (o) {
        e.console.assert(o, '[ImageRecord] options is required');
        e.console.assert(o.image, '[ImageRecord] options.image is required');
        this._image = o.image;
        this._tiles = [];
      };
      r.prototype = {
        destroy: function () {
          this._image = null;
          this._renderedContext = null;
          this._tiles = null;
        },
        getImage: function () {
          return this._image;
        },
        getRenderedContext: function () {
          if (!this._renderedContext) {
            var o = document.createElement('canvas');
            o.width = this._image.width;
            o.height = this._image.height;
            this._renderedContext = o.getContext('2d');
            this._renderedContext.drawImage(this._image, 0, 0);
            this._image = null;
          }
          return this._renderedContext;
        },
        setRenderedContext: function (o) {
          e.console.error(
            'ImageRecord.setRenderedContext is deprecated. The rendered context should be created by the ImageRecord itself when calling ImageRecord.getRenderedContext.'
          );
          this._renderedContext = o;
        },
        addTile: function (o) {
          e.console.assert(o, '[ImageRecord.addTile] tile is required');
          this._tiles.push(o);
        },
        removeTile: function (o) {
          for (var s = 0; s < this._tiles.length; s++) {
            if (this._tiles[s] === o) {
              this._tiles.splice(s, 1);
              return;
            }
          }
          e.console.warn(
            '[ImageRecord.removeTile] trying to remove unknown tile',
            o
          );
        },
        getTileCount: function () {
          return this._tiles.length;
        },
      };
      e.TileCache = function (o) {
        o = o || {};
        this._maxImageCacheCount =
          o.maxImageCacheCount || e.DEFAULT_SETTINGS.maxImageCacheCount;
        this._tilesLoaded = [];
        this._imagesLoaded = [];
        this._imagesLoadedCount = 0;
      };
      e.TileCache.prototype = {
        numTilesLoaded: function () {
          return this._tilesLoaded.length;
        },
        cacheTile: function (o) {
          e.console.assert(o, '[TileCache.cacheTile] options is required');
          e.console.assert(
            o.tile,
            '[TileCache.cacheTile] options.tile is required'
          );
          e.console.assert(
            o.tile.cacheKey,
            '[TileCache.cacheTile] options.tile.cacheKey is required'
          );
          e.console.assert(
            o.tiledImage,
            '[TileCache.cacheTile] options.tiledImage is required'
          );
          var s = o.cutoff || 0;
          var a = this._tilesLoaded.length;
          var l = this._imagesLoaded[o.tile.cacheKey];
          if (!l) {
            e.console.assert(
              o.image,
              '[TileCache.cacheTile] options.image is required to create an ImageRecord'
            );
            l = this._imagesLoaded[o.tile.cacheKey] = new r({ image: o.image });
            this._imagesLoadedCount++;
          }
          l.addTile(o.tile);
          o.tile.cacheImageRecord = l;
          if (this._imagesLoadedCount > this._maxImageCacheCount) {
            var u = null;
            var c = -1;
            var h = null;
            var d;
            var g;
            var y;
            var x;
            var b;
            var T;
            for (var f = this._tilesLoaded.length - 1; f >= 0; f--) {
              T = this._tilesLoaded[f];
              d = T.tile;
              if (!(d.level <= s) && !d.beingDrawn) {
                if (!u) {
                  u = d;
                  c = f;
                  h = T;
                  continue;
                }
                x = d.lastTouchTime;
                g = u.lastTouchTime;
                b = d.level;
                y = u.level;
                if (x < g || (x === g && b > y)) {
                  u = d;
                  c = f;
                  h = T;
                }
              }
            }
            if (u && c >= 0) {
              this._unloadTile(h);
              a = c;
            }
          }
          this._tilesLoaded[a] = new n({
            tile: o.tile,
            tiledImage: o.tiledImage,
          });
        },
        clearTilesFor: function (o) {
          e.console.assert(
            o,
            '[TileCache.clearTilesFor] tiledImage is required'
          );
          var s;
          for (var a = 0; a < this._tilesLoaded.length; ++a) {
            s = this._tilesLoaded[a];
            if (s.tiledImage === o) {
              this._unloadTile(s);
              this._tilesLoaded.splice(a, 1);
              a--;
            }
          }
        },
        getImageRecord: function (o) {
          e.console.assert(
            o,
            '[TileCache.getImageRecord] cacheKey is required'
          );
          return this._imagesLoaded[o];
        },
        _unloadTile: function (o) {
          e.console.assert(o, '[TileCache._unloadTile] tileRecord is required');
          var s = o.tile;
          var a = o.tiledImage;
          s.unload();
          s.cacheImageRecord = null;
          var l = this._imagesLoaded[s.cacheKey];
          l.removeTile(s);
          if (!l.getTileCount()) {
            l.destroy();
            delete this._imagesLoaded[s.cacheKey];
            this._imagesLoadedCount--;
          }
          a.viewer.raiseEvent('tile-unloaded', { tile: s, tiledImage: a });
        },
      };
    })();
    (function () {
      var e = t;
      e.World = function (n) {
        var r = this;
        e.console.assert(n.viewer, '[World] options.viewer is required');
        e.EventSource.call(this);
        this.viewer = n.viewer;
        this._items = [];
        this._needsDraw = false;
        this._autoRefigureSizes = true;
        this._needsSizesFigured = false;
        this._delegatedFigureSizes = function (o) {
          if (r._autoRefigureSizes) {
            r._figureSizes();
          } else {
            r._needsSizesFigured = true;
          }
        };
        this._figureSizes();
      };
      e.extend(e.World.prototype, e.EventSource.prototype, {
        addItem: function (n, r) {
          e.console.assert(n, '[World.addItem] item is required');
          e.console.assert(
            n instanceof e.TiledImage,
            '[World.addItem] only TiledImages supported at this time'
          );
          r = r || {};
          if (r.index === void 0) {
            this._items.push(n);
          } else {
            var o = Math.max(0, Math.min(this._items.length, r.index));
            this._items.splice(o, 0, n);
          }
          if (this._autoRefigureSizes) {
            this._figureSizes();
          } else {
            this._needsSizesFigured = true;
          }
          this._needsDraw = true;
          n.addHandler('bounds-change', this._delegatedFigureSizes);
          n.addHandler('clip-change', this._delegatedFigureSizes);
          this.raiseEvent('add-item', { item: n });
        },
        getItemAt: function (n) {
          e.console.assert(n !== void 0, '[World.getItemAt] index is required');
          return this._items[n];
        },
        getIndexOfItem: function (n) {
          e.console.assert(n, '[World.getIndexOfItem] item is required');
          return e.indexOf(this._items, n);
        },
        getItemCount: function () {
          return this._items.length;
        },
        setItemIndex: function (n, r) {
          e.console.assert(n, '[World.setItemIndex] item is required');
          e.console.assert(
            r !== void 0,
            '[World.setItemIndex] index is required'
          );
          var o = this.getIndexOfItem(n);
          if (r >= this._items.length) {
            throw new Error('Index bigger than number of layers.');
          }
          if (r !== o && o !== -1) {
            this._items.splice(o, 1);
            this._items.splice(r, 0, n);
            this._needsDraw = true;
            this.raiseEvent('item-index-change', {
              item: n,
              previousIndex: o,
              newIndex: r,
            });
          }
        },
        removeItem: function (n) {
          e.console.assert(n, '[World.removeItem] item is required');
          var r = e.indexOf(this._items, n);
          if (r !== -1) {
            n.removeHandler('bounds-change', this._delegatedFigureSizes);
            n.removeHandler('clip-change', this._delegatedFigureSizes);
            n.destroy();
            this._items.splice(r, 1);
            this._figureSizes();
            this._needsDraw = true;
            this._raiseRemoveItem(n);
          }
        },
        removeAll: function () {
          this.viewer._cancelPendingImages();
          var n;
          for (var r = 0; r < this._items.length; r++) {
            n = this._items[r];
            n.removeHandler('bounds-change', this._delegatedFigureSizes);
            n.removeHandler('clip-change', this._delegatedFigureSizes);
            n.destroy();
          }
          var o = this._items;
          this._items = [];
          this._figureSizes();
          this._needsDraw = true;
          for (r = 0; r < o.length; r++) {
            n = o[r];
            this._raiseRemoveItem(n);
          }
        },
        resetItems: function () {
          for (var n = 0; n < this._items.length; n++) {
            this._items[n].reset();
          }
        },
        update: function () {
          var n = false;
          for (var r = 0; r < this._items.length; r++) {
            n = this._items[r].update() || n;
          }
          return n;
        },
        draw: function () {
          for (var n = 0; n < this._items.length; n++) {
            this._items[n].draw();
          }
          this._needsDraw = false;
        },
        needsDraw: function () {
          for (var n = 0; n < this._items.length; n++) {
            if (this._items[n].needsDraw()) {
              return true;
            }
          }
          return this._needsDraw;
        },
        getHomeBounds: function () {
          return this._homeBounds.clone();
        },
        getContentFactor: function () {
          return this._contentFactor;
        },
        setAutoRefigureSizes: function (n) {
          this._autoRefigureSizes = n;
          if (n & this._needsSizesFigured) {
            this._figureSizes();
            this._needsSizesFigured = false;
          }
        },
        arrange: function (n) {
          n = n || {};
          var r = n.immediately || false;
          var o = n.layout || e.DEFAULT_SETTINGS.collectionLayout;
          var s = n.rows || e.DEFAULT_SETTINGS.collectionRows;
          var a = n.columns || e.DEFAULT_SETTINGS.collectionColumns;
          var l = n.tileSize || e.DEFAULT_SETTINGS.collectionTileSize;
          var u = n.tileMargin || e.DEFAULT_SETTINGS.collectionTileMargin;
          var c = l + u;
          var h;
          if (!n.rows && a) {
            h = a;
          } else {
            h = Math.ceil(this._items.length / s);
          }
          var d = 0;
          var g = 0;
          var y;
          var x;
          var b;
          var T;
          var f;
          this.setAutoRefigureSizes(false);
          for (var E = 0; E < this._items.length; E++) {
            if (E && E % h == 0) {
              if (o === 'horizontal') {
                g += c;
                d = 0;
              } else {
                d += c;
                g = 0;
              }
            }
            y = this._items[E];
            x = y.getBounds();
            if (x.width > x.height) {
              b = l;
            } else {
              b = l * (x.width / x.height);
            }
            T = b * (x.height / x.width);
            f = new e.Point(d + (l - b) / 2, g + (l - T) / 2);
            y.setPosition(f, r);
            y.setWidth(b, r);
            if (o === 'horizontal') {
              d += c;
            } else {
              g += c;
            }
          }
          this.setAutoRefigureSizes(true);
        },
        _figureSizes: function () {
          var n = this._homeBounds ? this._homeBounds.clone() : null;
          var r = this._contentSize ? this._contentSize.clone() : null;
          var o = this._contentFactor || 0;
          if (!this._items.length) {
            this._homeBounds = new e.Rect(0, 0, 1, 1);
            this._contentSize = new e.Point(1, 1);
            this._contentFactor = 1;
          } else {
            var s = this._items[0];
            var a = s.getBounds();
            this._contentFactor = s.getContentSize().x / a.width;
            var l = s.getClippedBounds().getBoundingBox();
            var u = l.x;
            var c = l.y;
            var h = l.x + l.width;
            var d = l.y + l.height;
            for (var g = 1; g < this._items.length; g++) {
              s = this._items[g];
              a = s.getBounds();
              this._contentFactor = Math.max(
                this._contentFactor,
                s.getContentSize().x / a.width
              );
              l = s.getClippedBounds().getBoundingBox();
              u = Math.min(u, l.x);
              c = Math.min(c, l.y);
              h = Math.max(h, l.x + l.width);
              d = Math.max(d, l.y + l.height);
            }
            this._homeBounds = new e.Rect(u, c, h - u, d - c);
            this._contentSize = new e.Point(
              this._homeBounds.width * this._contentFactor,
              this._homeBounds.height * this._contentFactor
            );
          }
          if (
            this._contentFactor !== o ||
            !this._homeBounds.equals(n) ||
            !this._contentSize.equals(r)
          ) {
            this.raiseEvent('metrics-change', {});
          }
        },
        _raiseRemoveItem: function (n) {
          this.raiseEvent('remove-item', { item: n });
        },
      });
    })();
  })();
  var Rt = hh.exports;
  const de = 'http://www.w3.org/2000/svg';
  const Xo = (i) => {
    const t = i.getAttribute('class');
    if (t) {
      return new Set(t.split(' '));
    } else {
      return new Set();
    }
  };
  const _t = (i, t) => {
    const e = Xo(i);
    e.add(t);
    i.setAttribute('class', Array.from(e).join(' '));
  };
  const Yo = (i, t) => {
    const e = Xo(i);
    e.delete(t);
    if (e.size === 0) {
      i.removeAttribute('class');
    } else {
      i.setAttribute('class', Array.from(e).join(' '));
    }
  };
  const Zo = (i, t) => Xo(i).has(t);
  const Mt = (i, t) => {
    const e = i.selector('FragmentSelector');
    if (
      e == null
        ? void 0
        : e.conformsTo.startsWith('http://www.w3.org/TR/media-frags')
    ) {
      const { value: n } = e;
      const r = n.includes(':')
        ? n.substring(n.indexOf('=') + 1, n.indexOf(':'))
        : 'pixel';
      const o = n.includes(':')
        ? n.substring(n.indexOf(':') + 1)
        : n.substring(n.indexOf('=') + 1);
      let [s, a, l, u] = o.split(',').map(parseFloat);
      if (r.toLowerCase() === 'percent') {
        s = (s * t.naturalWidth) / 100;
        a = (a * t.naturalHeight) / 100;
        l = (l * t.naturalWidth) / 100;
        u = (u * t.naturalHeight) / 100;
      }
      return { x: s, y: a, w: l, h: u };
    }
  };
  const BS = (i, t, e, n, r) => ({
    source: r == null ? void 0 : r.src,
    selector: {
      type: 'FragmentSelector',
      conformsTo: 'http://www.w3.org/TR/media-frags/',
      value: `xywh=pixel:${i},${t},${e},${n}`,
    },
  });
  const NS = (i, t, e, n, r) => {
    const o = (i / r.naturalWidth) * 100;
    const s = (t / r.naturalHeight) * 100;
    const a = (e / r.naturalWidth) * 100;
    const l = (n / r.naturalHeight) * 100;
    return {
      source: r.src,
      selector: {
        type: 'FragmentSelector',
        conformsTo: 'http://www.w3.org/TR/media-frags/',
        value: `xywh=percent:${o},${s},${a},${l}`,
      },
    };
  };
  const gn = (i, t, e, n, r, o) =>
    (o == null ? void 0 : o.toLowerCase()) === 'percent'
      ? NS(i, t, e, n, r)
      : BS(i, t, e, n, r);
  const $i = (i, t, e, n, r) => {
    i.setAttribute('x', t);
    i.setAttribute('y', e);
    i.setAttribute('width', n);
    i.setAttribute('height', r);
  };
  const er = (i, t, e) => {
    i.setAttribute('cx', t);
    i.setAttribute('cy', e);
    i.setAttribute('r', 7);
  };
  const dh = (i, t, e, n, r) => {
    const o = document.createElementNS(de, 'path');
    o.setAttribute('fill-rule', 'evenodd');
    const { naturalWidth: s, naturalHeight: a } = i;
    o.setAttribute(
      'd',
      `M0 0 h${s} v${a} h-${s} z M${t} ${e} h${n} v${r} h-${n} z`
    );
    return o;
  };
  const Ko = (i, t, e, n, r, o) => {
    const { naturalWidth: s, naturalHeight: a } = t;
    i.setAttribute(
      'd',
      `M0 0 h${s} v${a} h-${s} z M${e} ${n} h${r} v${o} h-${r} z`
    );
  };
  const Jo = (i, t, e, n) => {
    const {
      x: r,
      y: o,
      w: s,
      h: a,
    } = i.type === 'Annotation' || i.type === 'Selection'
      ? Mt(i, t)
      : { x: i, y: t, w: e, h: n };
    const l = document.createElementNS(de, 'g');
    if (s === 0 && a === 0) {
      _t(l, 'a9s-point');
      _t(l, 'a9s-non-scaling');
      l.setAttribute('transform-origin', `${r} ${o}`);
      const u = document.createElementNS(de, 'circle');
      const c = document.createElementNS(de, 'circle');
      c.setAttribute('class', 'a9s-inner');
      er(c, r, o);
      u.setAttribute('class', 'a9s-outer');
      er(u, r, o);
      l.appendChild(u);
      l.appendChild(c);
    } else {
      const u = document.createElementNS(de, 'rect');
      const c = document.createElementNS(de, 'rect');
      c.setAttribute('class', 'a9s-inner');
      $i(c, r, o, s, a);
      u.setAttribute('class', 'a9s-outer');
      $i(u, r, o, s, a);
      l.appendChild(u);
      l.appendChild(c);
    }
    return l;
  };
  const fh = (i) => {
    const t = i.querySelector('.a9s-outer');
    if (t.nodeName === 'rect') {
      const e = parseFloat(t.getAttribute('x'));
      const n = parseFloat(t.getAttribute('y'));
      const r = parseFloat(t.getAttribute('width'));
      const o = parseFloat(t.getAttribute('height'));
      return { x: e, y: n, w: r, h: o };
    } else {
      const e = parseFloat(t.getAttribute('cx'));
      const n = parseFloat(t.getAttribute('cy'));
      return { x: e, y: n, w: 0, h: 0 };
    }
  };
  const Qo = (i, t, e, n, r) => {
    const o = i.querySelector('.a9s-inner');
    const s = i.querySelector('.a9s-outer');
    if (s.nodeName === 'rect') {
      $i(o, t, e, n, r);
      $i(s, t, e, n, r);
    } else {
      er(o, t, e);
      er(s, t, e);
    }
  };
  const IS = (i, t) => {
    const { w: e, h: n } = Mt(i, t);
    return e * n;
  };
  class HS {
    constructor(t, e, n, r, o) {
      P(this, 'dragTo', (t, e) => {
        this.group.style.display = null;
        this.opposite = [t, e];
        const { x: n, y: r, w: o, h: s } = this.bbox;
        Ko(this.mask, this.env.image, n, r, o, s);
        Qo(this.rect, n, r, o, s);
      });
      P(this, 'getBoundingClientRect', () => this.rect.getBoundingClientRect());
      P(this, 'toSelection', () => {
        const { x: t, y: e, w: n, h: r } = this.bbox;
        return new Un(gn(t, e, n, r, this.env.image, this.config.fragmentUnit));
      });
      P(this, 'destroy', () => {
        this.group.parentNode.removeChild(this.group);
        this.mask = null;
        this.rect = null;
        this.group = null;
      });
      this.anchor = [t, e];
      this.opposite = [t, e];
      this.config = r;
      this.env = o;
      this.group = document.createElementNS(de, 'g');
      this.mask = dh(o.image, t, e, 2, 2);
      this.mask.setAttribute('class', 'a9s-selection-mask');
      this.rect = Jo(t, e, 2, 2);
      this.rect.setAttribute('class', 'a9s-selection');
      this.group.style.pointerEvents = 'none';
      this.group.style.display = 'none';
      this.group.appendChild(this.mask);
      this.group.appendChild(this.rect);
      n.appendChild(this.group);
    }
    get bbox() {
      const t = this.opposite[0] - this.anchor[0];
      const e = this.opposite[1] - this.anchor[1];
      return {
        x: t > 0 ? this.anchor[0] : this.opposite[0],
        y: e > 0 ? this.anchor[1] : this.opposite[1],
        w: Math.max(1, Math.abs(t)),
        h: Math.max(1, Math.abs(e)),
      };
    }
    get element() {
      return this.rect;
    }
  }
  const zS = {
    touchstart: 'mousedown',
    touchmove: 'mousemove',
    touchend: 'mouseup',
  };
  const $o = () =>
    'ontouchstart' in window ||
    navigator.maxTouchPoints > 0 ||
    navigator.msMaxTouchPoints > 0;
  const VS = (i) => {
    let t = null;
    const e = (r, o) =>
      new MouseEvent(r, {
        screenX: o.screenX,
        screenY: o.screenY,
        clientX: o.clientX,
        clientY: o.clientY,
        pageX: o.pageX,
        pageY: o.pageY,
        bubbles: true,
      });
    const n = (r) => {
      const o = r.changedTouches[0];
      const s = e(zS[r.type], o);
      o.target.dispatchEvent(s);
      r.preventDefault();
      if (r.type === 'touchstart' || r.type === 'touchmove') {
        if (t) {
          clearTimeout(t);
        }
        t = setTimeout(() => {
          const a = e('dblclick', o);
          o.target.dispatchEvent(a);
        }, 800);
      }
      if (r.type === 'touchend' && t) {
        clearTimeout(t);
      }
    };
    i.addEventListener('touchstart', n, true);
    i.addEventListener('touchmove', n, true);
    i.addEventListener('touchend', n, true);
    i.addEventListener('touchcancel', n, true);
  };
  const tr = 'An implementation is missing';
  const US = $o();
  class ph extends Qn {
    constructor(t, e, n) {
      super();
      P(this, 'enableResponsive', () => {
        if (window.ResizeObserver) {
          this.resizeObserver = new ResizeObserver(() => {
            const t = this.svg.getBoundingClientRect();
            const { width: e, height: n } = this.svg.viewBox.baseVal;
            this.scale = Math.max(e / t.width, n / t.height);
            if (this.onScaleChanged) {
              this.onScaleChanged(this.scale);
            }
          });
          this.resizeObserver.observe(this.svg.parentNode);
        }
      });
      P(this, 'getSVGPoint', (t) => {
        const e = this.svg.createSVGPoint();
        if (US) {
          const n = this.svg.getBoundingClientRect();
          const r = t.clientX - n.x;
          const o = t.clientY - n.y;
          const { left: s, top: a } = this.svg.getBoundingClientRect();
          e.x = r + s;
          e.y = o + a;
          return e.matrixTransform(this.g.getScreenCTM().inverse());
        } else {
          e.x = t.offsetX;
          e.y = t.offsetY;
          return e.matrixTransform(this.g.getCTM().inverse());
        }
      });
      P(this, 'drawHandle', (t, e) => {
        const n = document.createElementNS(de, 'g');
        n.setAttribute('class', 'a9s-handle');
        const r = document.createElementNS(de, 'g');
        const o = (u) => {
          const c = document.createElementNS(de, 'circle');
          c.setAttribute('cx', t);
          c.setAttribute('cy', e);
          c.setAttribute('r', u);
          c.setAttribute('transform-origin', `${t} ${e}`);
          return c;
        };
        const s = this.config.handleRadius || 6;
        const a = o(s);
        a.setAttribute('class', 'a9s-handle-inner');
        const l = o(s + 1);
        l.setAttribute('class', 'a9s-handle-outer');
        r.appendChild(l);
        r.appendChild(a);
        n.appendChild(r);
        return n;
      });
      P(this, 'setHandleXY', (t, e, n) => {
        const r = t.querySelector('.a9s-handle-inner');
        r.setAttribute('cx', e);
        r.setAttribute('cy', n);
        r.setAttribute('transform-origin', `${e} ${n}`);
        const o = t.querySelector('.a9s-handle-outer');
        o.setAttribute('cx', e);
        o.setAttribute('cy', n);
        o.setAttribute('transform-origin', `${e} ${n}`);
      });
      P(this, 'getHandleXY', (t) => {
        const e = t.querySelector('.a9s-handle-outer');
        return {
          x: parseFloat(e.getAttribute('cx')),
          y: parseFloat(e.getAttribute('cy')),
        };
      });
      P(this, 'scaleHandle', (t) => {
        const e = t.querySelector('.a9s-handle-inner');
        const n = t.querySelector('.a9s-handle-outer');
        const r = this.scale * (this.config.handleRadius || 6);
        e.setAttribute('r', r);
        n.setAttribute('r', r);
      });
      this.svg = t.closest('svg');
      this.g = t;
      this.config = e;
      this.env = n;
      this.scale = 1;
      const { image: r } = n;
      if (r instanceof Element || r instanceof HTMLDocument) {
        this.enableResponsive();
      }
    }
    destroy() {
      if (this.resizeObserver) {
        this.resizeObserver.disconnect();
      }
      this.resizeObserver = null;
    }
  }
  class es extends ph {
    constructor(t, e, n) {
      super(t, e, n);
      P(
        this,
        'attachListeners',
        ({ mouseMove: t, mouseUp: e, dblClick: n }) => {
          if (t) {
            this.mouseMove = (r) => {
              const { x: o, y: s } = this.getSVGPoint(r);
              if (!this.started) {
                this.emit('startSelection', { x: o, y: s });
                this.started = true;
              }
              t(o, s, r);
            };
            this.svg.addEventListener('mousemove', this.mouseMove);
          }
          if (e) {
            this.mouseUp = (r) => {
              if (r.button !== 0) {
                return;
              }
              const { x: o, y: s } = this.getSVGPoint(r);
              e(o, s, r);
            };
            document.addEventListener('mouseup', this.mouseUp);
          }
          if (n) {
            this.dblClick = (r) => {
              const { x: o, y: s } = this.getSVGPoint(r);
              n(o, s, r);
            };
            document.addEventListener('dblclick', this.dblClick);
          }
        }
      );
      P(this, 'detachListeners', () => {
        if (this.mouseMove) {
          this.svg.removeEventListener('mousemove', this.mouseMove);
        }
        if (this.mouseUp) {
          document.removeEventListener('mouseup', this.mouseUp);
        }
        if (this.dblClick) {
          document.removeEventListener('dblclick', this.dblClick);
        }
      });
      P(this, 'start', (t, e) => {
        const { x: n, y: r } = this.getSVGPoint(t);
        this.startDrawing(n, r, e, t);
      });
      P(this, 'startDrawing', (t) => {
        throw new Error(tr);
      });
      P(this, 'createEditableShape', (t, e) => {
        throw new Error(tr);
      });
      this.started = false;
    }
    get isDrawing() {
      throw new Error(tr);
    }
  }
  es.supports = (i) => {
    throw new Error(tr);
  };
  const mh = 'An implementation is missing';
  class gh extends ph {
    constructor(t, e, n, r) {
      super(e, n, r);
      P(this, 'updateState', (t) => {
        throw new Error(mh);
      });
      this.annotation = t;
    }
    get element() {
      throw new Error(mh);
    }
  }
  const WS = /firefox/i.test(navigator.userAgent);
  const vh = (i, t, e, n, r) => {
    i.setAttribute('width', n);
    i.setAttribute('height', r);
    if (WS) {
      i.setAttribute('x', 0);
      i.setAttribute('y', 0);
      i.setAttribute('transform', `translate(${t}, ${e})`);
    } else {
      i.setAttribute('x', t);
      i.setAttribute('y', e);
    }
  };
  const jS = (i, t) => {
    const { x: e, y: n, width: r, height: o } = t.getBBox();
    const s = document.createElementNS(de, 'svg');
    s.setAttribute('class', 'a9s-formatter-el');
    vh(s, e, n, r, o);
    const a = document.createElementNS(de, 'g');
    a.appendChild(i);
    s.appendChild(a);
    t.append(s);
  };
  const nr = (i, t, e) => {
    if (!e) {
      return i;
    }
    const n = e.reduce((a, l) => {
      const u = l(t);
      if (!u) {
        return a;
      }
      if (typeof u == 'string' || u instanceof String) {
        a.className = a.className ? `${a.className} ${u}` : u;
      } else if (u.nodeType === Node.ELEMENT_NODE) {
        a.elements = a.elements ? [...a.elements, u] : [u];
      } else {
        const { className: c, style: h, element: d } = u;
        if (c) {
          a.className = a.className ? `${a.className} ${c}` : c;
        }
        if (h) {
          a.style = a.style ? `${a.style} ${h}` : h;
        }
        if (d) {
          a.elements = a.elements ? [...a.elements, d] : [d];
        }
      }
      for (const c in u) {
        if (u.hasOwnProperty(c) && c.startsWith('data-')) {
          a[c] = u[c];
        }
      }
      return a;
    }, {});
    const { className: r, style: o, elements: s } = n;
    if (r) {
      _t(i, r);
    }
    if (o) {
      const a = i.querySelector('.a9s-outer');
      const l = i.querySelector('.a9s-inner');
      if (a && l) {
        a.setAttribute('style', 'display:none');
        l.setAttribute('style', o);
      } else {
        i.setAttribute('style', o);
      }
    }
    if (s) {
      s.forEach((a) => jS(a, i));
    }
    for (const a in n) {
      if (n.hasOwnProperty(a) && a.startsWith('data-')) {
        i.setAttribute(a, n[a]);
      }
    }
  };
  const ts = (i, t, e, n, r) => {
    const o = i.querySelector('.a9s-formatter-el');
    if (o) {
      vh(o, t, e, n, r);
    }
  };
  class GS extends gh {
    constructor(t, e, n, r) {
      super(t, e, n, r);
      P(this, 'onScaleChanged', () => this.handles.map(this.scaleHandle));
      P(this, 'setSize', (t, e, n, r) => {
        Qo(this.rectangle, t, e, n, r);
        Ko(this.mask, this.env.image, t, e, n, r);
        ts(this.elementGroup, t, e, n, r);
        const [o, s, a, l] = this.handles;
        this.setHandleXY(o, t, e);
        this.setHandleXY(s, t + n, e);
        this.setHandleXY(a, t + n, e + r);
        this.setHandleXY(l, t, e + r);
      });
      P(this, 'stretchCorners', (t, e, n) => {
        const r = this.getHandleXY(e);
        const o = n.x - r.x;
        const s = n.y - r.y;
        const a = o > 0 ? r.x : n.x;
        const l = s > 0 ? r.y : n.y;
        const u = Math.abs(o);
        const c = Math.abs(s);
        Qo(this.rectangle, a, l, u, c);
        Ko(this.mask, this.env.image, a, l, u, c);
        ts(this.elementGroup, a, l, u, c);
        this.setHandleXY(this.handles[t], n.x, n.y);
        const h = this.handles[(t + 3) % 4];
        this.setHandleXY(h, r.x, n.y);
        const d = this.handles[(t + 5) % 4];
        this.setHandleXY(d, n.x, r.y);
        return { x: a, y: l, w: u, h: c };
      });
      P(this, 'onGrab', (t) => (e) => {
        if (e.button !== 0) {
          return;
        }
        this.grabbedElem = t;
        const n = this.getSVGPoint(e);
        const { x: r, y: o } = fh(this.rectangle);
        this.mouseOffset = { x: n.x - r, y: n.y - o };
      });
      P(this, 'onMouseMove', (t) => {
        if (t.button !== 0) {
          return;
        }
        const e = (n, r) => (n < 0 ? 0 : n > r ? r : n);
        if (this.grabbedElem) {
          const n = this.getSVGPoint(t);
          if (this.grabbedElem === this.rectangle) {
            const { w: r, h: o } = fh(this.rectangle);
            const { naturalWidth: s, naturalHeight: a } = this.env.image;
            const l = e(n.x - this.mouseOffset.x, s - r);
            const u = e(n.y - this.mouseOffset.y, a - o);
            this.setSize(l, u, r, o);
            this.emit(
              'update',
              gn(l, u, r, o, this.env.image, this.config.fragmentUnit)
            );
          } else {
            const r = this.handles.indexOf(this.grabbedElem);
            const o = r < 2 ? this.handles[r + 2] : this.handles[r - 2];
            const { x: s, y: a, w: l, h: u } = this.stretchCorners(r, o, n);
            this.emit(
              'update',
              gn(s, a, l, u, this.env.image, this.config.fragmentUnit)
            );
          }
        }
      });
      P(this, 'onMouseUp', (t) => {
        this.grabbedElem = null;
        this.mouseOffset = null;
      });
      P(this, 'updateState', (t) => {
        const { x: e, y: n, w: r, h: o } = Mt(t, this.env.image);
        this.setSize(e, n, r, o);
      });
      this.svg.addEventListener('mousemove', this.onMouseMove);
      this.svg.addEventListener('mouseup', this.onMouseUp);
      const { x: o, y: s, w: a, h: l } = Mt(t, r.image);
      this.containerGroup = document.createElementNS(de, 'g');
      this.mask = dh(r.image, o, s, a, l);
      this.mask.setAttribute('class', 'a9s-selection-mask');
      this.containerGroup.appendChild(this.mask);
      this.elementGroup = document.createElementNS(de, 'g');
      this.elementGroup.setAttribute(
        'class',
        'a9s-annotation editable selected'
      );
      this.elementGroup.setAttribute('data-id', t.id);
      this.rectangle = Jo(o, s, a, l);
      this.rectangle
        .querySelector('.a9s-inner')
        .addEventListener('mousedown', this.onGrab(this.rectangle));
      this.elementGroup.appendChild(this.rectangle);
      this.handles = [
        [o, s],
        [o + a, s],
        [o + a, s + l],
        [o, s + l],
      ].map((u) => {
        const [c, h] = u;
        const d = this.drawHandle(c, h);
        d.addEventListener('mousedown', this.onGrab(d));
        this.elementGroup.appendChild(d);
        return d;
      });
      this.containerGroup.appendChild(this.elementGroup);
      e.appendChild(this.containerGroup);
      nr(this.rectangle, t, n.formatters);
      this.grabbedElem = null;
      this.mouseOffset = null;
    }
    get element() {
      return this.elementGroup;
    }
    destroy() {
      this.containerGroup.parentNode.removeChild(this.containerGroup);
      super.destroy();
    }
  }
  class ir extends es {
    constructor(t, e, n) {
      super(t, e, n);
      P(this, 'startDrawing', (t, e) => {
        this.attachListeners({
          mouseMove: this.onMouseMove,
          mouseUp: this.onMouseUp,
        });
        this.rubberband = new HS(t, e, this.g, this.config, this.env);
      });
      P(this, 'stop', () => {
        if (this.rubberband) {
          this.rubberband.destroy();
          this.rubberband = null;
        }
      });
      P(this, 'onMouseMove', (t, e) => this.rubberband.dragTo(t, e));
      P(this, 'onMouseUp', () => {
        this.detachListeners();
        this.started = false;
        const { width: t, height: e } = this.rubberband.getBoundingClientRect();
        const n = this.config.minSelectionWidth || 4;
        const r = this.config.minSelectionHeight || 4;
        if (t >= n && e >= r) {
          const { element: o } = this.rubberband;
          o.annotation = this.rubberband.toSelection();
          this.emit('complete', o);
        } else {
          this.emit('cancel');
        }
        this.stop();
      });
      P(
        this,
        'createEditableShape',
        (t, e) =>
          new GS(
            t,
            this.g,
            je(ue({}, this.config), { formatters: e }),
            this.env
          )
      );
      this.rubberband = null;
    }
    get isDrawing() {
      return this.rubberband != null;
    }
  }
  ir.identifier = 'rect';
  ir.supports = (i) => {
    const t = i.selector('FragmentSelector');
    if (t == null) {
      return;
    } else {
      return t.conformsTo.startsWith('http://www.w3.org/TR/media-frags');
    }
  };
  const rr = (i) => {
    let t = 0;
    let e = i.length - 1;
    for (let n = 0; n < i.length; n++) {
      t += (i[e][0] + i[n][0]) * (i[e][1] - i[n][1]);
      e = n;
    }
    return Math.abs(0.5 * t);
  };
  const qS = (i, t, e, n) => {
    const r = i[0] - t;
    const o = i[1] - e;
    return Math.sqrt(r * r + o * o) <= n;
  };
  const XS = (i, t, e, n, r, o) => {
    const s = o || 0;
    const a = Math.cos(s);
    const l = Math.sin(s);
    const u = i[0] - t;
    const c = i[1] - e;
    const h = a * u + l * c;
    const d = l * u - a * c;
    return (h * h) / (n * n) + (d * d) / (r * r) <= 1;
  };
  const ns = (i, t) => {
    const e = i[0];
    const n = i[1];
    let r = false;
    let o = 0;
    for (let s = t.length - 1; o < t.length; s = o++) {
      const a = t[o][0];
      const l = t[o][1];
      const u = t[s][0];
      const c = t[s][1];
      if (l > n != c > n && e < ((u - a) * (n - l)) / (c - l) + a) {
        r = !r;
      }
    }
    return r;
  };
  const YS = (i, t) => {
    for (let e of i) {
      if (!ns(e, t)) {
        return false;
      }
    }
    return true;
  };
  const yh = (i) => {
    const t = i
      .getAttribute('d')
      .split(/(?=M|m|L|l|H|h|V|v|Z|z)/g)
      .map((r) => r.trim());
    const e = [];
    let n = [];
    for (let r of t) {
      const o = r.substring(0, 1);
      if (o.toLowerCase() === 'z') {
        e.push([...n]);
        n = [];
      } else {
        const s = r
          .substring(1)
          .split(' ')
          .map((c) => parseFloat(c.trim()));
        const a = o === o.toUpperCase();
        const l = a ? s[0] : s[0] + n[n.length - 1][0];
        const u = a ? s[1] : s[1] + n[n.length - 1][1];
        n.push([l, u]);
      }
    }
    if (n.length > 0) {
      e.push([...n]);
    }
    return e;
  };
  const ZS = (i) => {
    const n = new XMLSerializer()
      .serializeToString(i.documentElement)
      .replace('<svg>', `<svg xmlns="${de}">`);
    return new DOMParser().parseFromString(n, 'image/svg+xml').documentElement;
  };
  const wh = (i) => {
    const t = (n) => {
      Array.from(n.attributes).forEach((r) => {
        if (r.name.startsWith('on')) {
          n.removeAttribute(r.name);
        }
      });
    };
    const e = i.getElementsByTagName('script');
    Array.from(e)
      .reverse()
      .forEach((n) => n.parentNode.removeChild(n));
    t(i);
    Array.from(i.querySelectorAll('*')).forEach(t);
    return i;
  };
  const Gt = (i) => {
    const t = i.selector('SvgSelector');
    if (t) {
      const e = new DOMParser();
      const { value: n } = t;
      const r = e.parseFromString(n, 'image/svg+xml');
      const o = r.lookupPrefix(de);
      const s = r.lookupNamespaceURI(null);
      if (o || s) {
        return wh(r).firstChild;
      } else {
        return wh(ZS(r)).firstChild;
      }
    }
  };
  const bh = (i) => {
    const t = Gt(i);
    const e = document.createElementNS(de, 'g');
    const n = t.cloneNode(true);
    n.setAttribute('class', 'a9s-inner');
    const r = t.cloneNode(true);
    r.setAttribute('class', 'a9s-outer');
    e.appendChild(r);
    e.appendChild(n);
    return e;
  };
  const is = (i, t) => {
    const e = i.querySelector('.a9s-inner').cloneNode(true);
    e.removeAttribute('class');
    e.removeAttribute('xmlns');
    let n = e.outerHTML || new XMLSerializer().serializeToString(e);
    n = n.replace(` xmlns="${de}"`, '');
    return {
      source: t == null ? void 0 : t.src,
      selector: { type: 'SvgSelector', value: `<svg>${n}</svg>` },
    };
  };
  const KS = (i) => {
    const t = Gt(i);
    const e = t.nodeName.toLowerCase();
    if (e === 'polygon') {
      return JS(t);
    }
    if (e === 'circle') {
      return QS(t);
    }
    if (e === 'ellipse') {
      return $S(t);
    }
    if (e == 'path') {
      return eE(t);
    }
    throw `Unsupported SVG shape type: ${e}`;
  };
  const JS = (i) => {
    const t = i
      .getAttribute('points')
      .trim()
      .split(' ')
      .map((e) => e.split(',').map((n) => parseFloat(n.trim())));
    return rr(t);
  };
  const QS = (i) => {
    const t = i.getAttribute('r');
    return t * t * Math.PI;
  };
  const $S = (i) => {
    const t = i.getAttribute('rx');
    const e = i.getAttribute('ry');
    return t * e * Math.PI;
  };
  const eE = (i) => {
    const t = yh(i);
    if (t.length == 1) {
      return rr(t[0]);
    }
    {
      const e = (r) =>
        t.find((o) => {
          if (r !== o) {
            return YS(r, o);
          }
        });
      let n = 0;
      for (let r of t) {
        if (e(r)) {
          n -= rr(r);
        } else {
          n += rr(r);
        }
      }
      return n;
    }
  };
  class Sh {
    constructor(t, e) {
      P(this, 'redraw', () => {
        this.mask.setAttribute(
          'd',
          `M0 0 h${this.w} v${this.h} h-${
            this.w
          } z M${this.polygon.getAttribute('points')} z`
        );
      });
      P(this, 'destroy', () => this.mask.parentNode.removeChild(this.mask));
      this.w = t.naturalWidth;
      this.h = t.naturalHeight;
      this.polygon = e;
      this.mask = document.createElementNS(de, 'path');
      this.mask.setAttribute('fill-rule', 'evenodd');
      this.mask.setAttribute('class', 'a9s-selection-mask');
      this.mask.setAttribute(
        'd',
        `M0 0 h${this.w} v${this.h} h-${this.w} z M${this.polygon.getAttribute(
          'points'
        )} z`
      );
    }
    get element() {
      return this.mask;
    }
  }
  class tE {
    constructor(t, e, n) {
      P(this, 'setPoints', (t) => {
        const e = t.map((n) => `${n[0]},${n[1]}`).join(' ');
        this.outer.setAttribute('points', e);
        this.inner.setAttribute('points', e);
      });
      P(this, 'getBoundingClientRect', () =>
        this.outer.getBoundingClientRect()
      );
      P(this, 'dragTo', (t) => {
        this.group.style.display = null;
        this.mousepos = t;
        const e = [...this.points, t];
        this.setPoints(e);
        this.mask.redraw();
      });
      P(this, 'addPoint', () => {
        const [t, e] = this.mousepos;
        const n = this.points[this.points.length - 1];
        if (Math.pow(t - n[0], 2) + Math.pow(e - n[1], 2) > 4) {
          this.points = [...this.points, this.mousepos];
          this.setPoints(this.points);
          this.mask.redraw();
        }
      });
      P(this, 'destroy', () => {
        this.group.parentNode.removeChild(this.group);
        this.polygon = null;
        this.group = null;
      });
      P(this, 'toSelection', () => new Un(is(this.group, this.env.image)));
      this.points = [t];
      this.env = n;
      this.mousepos = t;
      this.group = document.createElementNS(de, 'g');
      this.polygon = document.createElementNS(de, 'g');
      this.polygon.setAttribute('class', 'a9s-selection');
      this.outer = document.createElementNS(de, 'polygon');
      this.outer.setAttribute('class', 'a9s-outer');
      this.inner = document.createElementNS(de, 'polygon');
      this.inner.setAttribute('class', 'a9s-inner');
      this.setPoints(this.points);
      this.mask = new Sh(n.image, this.inner);
      this.polygon.appendChild(this.outer);
      this.polygon.appendChild(this.inner);
      this.group.style.display = 'none';
      this.group.appendChild(this.mask.element);
      this.group.appendChild(this.polygon);
      e.appendChild(this.group);
    }
    get element() {
      return this.polygon;
    }
  }
  const rs = (i) => {
    const t = i.querySelector('.a9s-inner').points;
    const e = [];
    for (let n = 0; n < t.numberOfItems; n++) {
      e.push(t.getItem(n));
    }
    return e;
  };
  const nE = (i) => i.querySelector('.a9s-inner').getBBox();
  class iE extends gh {
    constructor(t, e, n, r) {
      super(t, e, n, r);
      P(this, 'onScaleChanged', () => this.handles.map(this.scaleHandle));
      P(this, 'setPoints', (t) => {
        const e = (c) => Math.round(10 * c) / 10;
        const n = t.map((c) => `${e(c.x)},${e(c.y)}`).join(' ');
        this.shape.querySelector('.a9s-inner').setAttribute('points', n);
        const o = this.shape.querySelector('.a9s-outer');
        o.setAttribute('points', n);
        this.mask.redraw();
        const { x: s, y: a, width: l, height: u } = o.getBBox();
        ts(this.elementGroup, s, a, l, u);
      });
      P(this, 'onGrab', (t) => (e) => {
        if (e.button === 0) {
          this.grabbedElem = t;
          this.grabbedAt = this.getSVGPoint(e);
        }
      });
      P(this, 'onMouseMove', (t) => {
        const e = (n, r, o) => (n + r < 0 ? -n : n + r > o ? o - n : r);
        if (this.grabbedElem) {
          const n = this.getSVGPoint(t);
          if (this.grabbedElem === this.shape) {
            const { x: r, y: o, width: s, height: a } = nE(this.shape);
            const { naturalWidth: l, naturalHeight: u } = this.env.image;
            const c = e(r, n.x - this.grabbedAt.x, l - s);
            const h = e(o, n.y - this.grabbedAt.y, u - a);
            const d = rs(this.shape).map((g) => ({ x: g.x + c, y: g.y + h }));
            this.grabbedAt = n;
            this.setPoints(d);
            d.forEach((g, y) => this.setHandleXY(this.handles[y], g.x, g.y));
            this.emit('update', is(this.shape, this.env.image));
          } else {
            const r = this.handles.indexOf(this.grabbedElem);
            const o = rs(this.shape).map((s, a) => (a === r ? n : s));
            this.setPoints(o);
            this.setHandleXY(this.handles[r], n.x, n.y);
            this.emit('update', is(this.shape, this.env.image));
          }
        }
      });
      P(this, 'onMouseUp', (t) => {
        this.grabbedElem = null;
        this.grabbedAt = null;
      });
      P(this, 'updateState', (t) => {
        const e = Gt(t)
          .getAttribute('points')
          .split(' ')
          .map((n) => {
            const [r, o] = n.split(',').map((s) => parseFloat(s.trim()));
            return { x: r, y: o };
          });
        this.setPoints(e);
        e.forEach((n, r) => this.setHandleXY(this.handles[r], n.x, n.y));
      });
      P(this, 'destroy', () => {
        this.containerGroup.parentNode.removeChild(this.containerGroup);
        super.destroy();
      });
      this.svg.addEventListener('mousemove', this.onMouseMove);
      this.svg.addEventListener('mouseup', this.onMouseUp);
      this.containerGroup = document.createElementNS(de, 'g');
      this.shape = bh(t);
      this.shape
        .querySelector('.a9s-inner')
        .addEventListener('mousedown', this.onGrab(this.shape));
      this.mask = new Sh(r.image, this.shape.querySelector('.a9s-inner'));
      this.containerGroup.appendChild(this.mask.element);
      this.elementGroup = document.createElementNS(de, 'g');
      this.elementGroup.setAttribute(
        'class',
        'a9s-annotation editable selected'
      );
      this.elementGroup.setAttribute('data-id', t.id);
      this.elementGroup.appendChild(this.shape);
      this.handles = rs(this.shape).map((o) => {
        const s = this.drawHandle(o.x, o.y);
        s.addEventListener('mousedown', this.onGrab(s));
        this.elementGroup.appendChild(s);
        return s;
      });
      this.containerGroup.appendChild(this.elementGroup);
      e.appendChild(this.containerGroup);
      nr(this.shape, t, n.formatters);
      this.grabbedElem = null;
      this.grabbedAt = null;
    }
    get element() {
      return this.elementGroup;
    }
  }
  class os extends es {
    constructor(t, e, n) {
      super(t, e, n);
      P(this, 'startDrawing', (t, e, n) => {
        this._isDrawing = true;
        this._startOnSingleClick = n;
        this.attachListeners({
          mouseMove: this.onMouseMove,
          mouseUp: this.onMouseUp,
          dblClick: this.onDblClick,
        });
        this.rubberband = new tE([t, e], this.g, this.env);
      });
      P(this, 'stop', () => {
        this.detachListeners();
        this._isDrawing = false;
        if (this.rubberband) {
          this.rubberband.destroy();
          this.rubberband = null;
        }
      });
      P(this, 'onMouseMove', (t, e) => this.rubberband.dragTo([t, e]));
      P(this, 'onMouseUp', () => {
        const { width: t, height: e } = this.rubberband.getBoundingClientRect();
        const n = this.config.minSelectionWidth || 4;
        const r = this.config.minSelectionHeight || 4;
        if (t >= n || e >= r) {
          this.rubberband.addPoint();
        } else if (!this._startOnSingleClick) {
          this.emit('cancel');
          this.stop();
        }
      });
      P(this, 'onDblClick', () => {
        this._isDrawing = false;
        const t = this.rubberband.element;
        t.annotation = this.rubberband.toSelection();
        this.emit('complete', t);
        this.stop();
      });
      P(
        this,
        'createEditableShape',
        (t, e) =>
          new iE(
            t,
            this.g,
            je(ue({}, this.config), { formatters: e }),
            this.env
          )
      );
      this._isDrawing = false;
      this._startOnSingleClick = false;
    }
    get isDrawing() {
      return this._isDrawing;
    }
  }
  os.identifier = 'polygon';
  os.supports = (i) => {
    var e;
    const t = i.selector('SvgSelector');
    if (t) {
      if ((e = t.value) == null) {
        return;
      } else {
        return e.match(/^<svg.*<polygon/g);
      }
    }
  };
  class rE extends Qn {
    constructor(t, e, n) {
      super();
      P(this, 'listTools', () => this._registered.map((t) => t.identifier));
      P(this, 'registerTool', (t) => {
        const e = t.identifier;
        if (this.listTools().includes(e)) {
          this.unregisterTool(e);
        }
        this._registered.unshift(t);
      });
      P(
        this,
        'unregisterTool',
        (t) =>
          (this._registered = this._registered.filter(
            (e) => e.identifier !== t
          ))
      );
      P(this, 'setCurrent', (t) => {
        const e =
          typeof t == 'string' || t instanceof String
            ? this._registered.find((n) => n.identifier === t)
            : t;
        this._current = new e(this._g, this._config, this._env);
        this._current.on('startSelection', (n) =>
          this.emit('startSelection', n)
        );
        this._current.on('complete', (n) => this.emit('complete', n));
        this._current.on('cancel', (n) => this.emit('cancel', n));
      });
      P(this, 'forAnnotation', (t) => {
        var s;
        const [e, ...n] = t.targets;
        const r = (s = e.renderedVia) == null ? void 0 : s.name;
        const o = r
          ? this._registered.find((a) => a.identifier === r)
          : this._registered.find((a) => a.supports(t));
        if (o) {
          return new o(this._g, this._config, this._env);
        } else {
          return null;
        }
      });
      this._g = t;
      this._config = e;
      this._env = n;
      this._registered = [ir, os];
      this.setCurrent(ir);
    }
    get current() {
      return this._current;
    }
  }
  class oE {
    constructor(t, e, n) {
      this.svg = t.closest('svg');
      this.g = document.createElementNS(de, 'g');
      this.g.setAttribute('class', 'a9s-crosshair');
      const r = document.createElementNS(de, 'line');
      const o = document.createElementNS(de, 'line');
      this.g.appendChild(r);
      this.g.appendChild(o);
      t.appendChild(this.g);
      const s = (a) => {
        const l = this.svg.getBoundingClientRect();
        const u = a.clientX - l.x;
        const c = a.clientY - l.y;
        const h = this.svg.createSVGPoint();
        const { left: d, top: g } = this.svg.getBoundingClientRect();
        h.x = u + d;
        h.y = c + g;
        return h.matrixTransform(t.getScreenCTM().inverse());
      };
      this.svg.addEventListener('mousemove', (a) => {
        const { x: l, y: u } = s(a);
        r.setAttribute('x1', 0);
        r.setAttribute('y1', u);
        r.setAttribute('x2', e);
        r.setAttribute('y2', u);
        o.setAttribute('x1', l);
        o.setAttribute('y1', 0);
        o.setAttribute('x2', l);
        o.setAttribute('y2', n);
      });
    }
  }
  const sE = { FragmentSelector: Jo, SvgSelector: bh };
  const aE = { FragmentSelector: IS, SvgSelector: KS };
  const Eh = (i) => {
    const t = i.targets[0];
    if (t) {
      if (Array.isArray(t.selector)) {
        return t.selector[0];
      } else {
        return t.selector;
      }
    }
  };
  const ss = (i, t) => sE[Eh(i).type](i, t);
  const _h = (i, t) => aE[Eh(i).type](i, t);
  class cE {
    constructor(t = 9) {
      this._maxEntries = Math.max(4, t);
      this._minEntries = Math.max(2, Math.ceil(this._maxEntries * 0.4));
      this.clear();
    }
    all() {
      return this._all(this.data, []);
    }
    search(t) {
      let e = this.data;
      const n = [];
      if (!sr(t, e)) {
        return n;
      }
      const r = this.toBBox;
      const o = [];
      while (e) {
        for (let s = 0; s < e.children.length; s++) {
          const a = e.children[s];
          const l = e.leaf ? r(a) : a;
          if (sr(t, l)) {
            if (e.leaf) {
              n.push(a);
            } else if (ls(t, l)) {
              this._all(a, n);
            } else {
              o.push(a);
            }
          }
        }
        e = o.pop();
      }
      return n;
    }
    collides(t) {
      let e = this.data;
      if (!sr(t, e)) {
        return false;
      }
      const n = [];
      while (e) {
        for (let r = 0; r < e.children.length; r++) {
          const o = e.children[r];
          const s = e.leaf ? this.toBBox(o) : o;
          if (sr(t, s)) {
            if (e.leaf || ls(t, s)) {
              return true;
            }
            n.push(o);
          }
        }
        e = n.pop();
      }
      return false;
    }
    load(t) {
      if (!t || !t.length) {
        return this;
      }
      if (t.length < this._minEntries) {
        for (let n = 0; n < t.length; n++) {
          this.insert(t[n]);
        }
        return this;
      }
      let e = this._build(t.slice(), 0, t.length - 1, 0);
      if (!this.data.children.length) {
        this.data = e;
      } else if (this.data.height === e.height) {
        this._splitRoot(this.data, e);
      } else {
        if (this.data.height < e.height) {
          const n = this.data;
          this.data = e;
          e = n;
        }
        this._insert(e, this.data.height - e.height - 1, true);
      }
      return this;
    }
    insert(t) {
      if (t) {
        this._insert(t, this.data.height - 1);
      }
      return this;
    }
    clear() {
      this.data = yn([]);
      return this;
    }
    remove(t, e) {
      if (!t) {
        return this;
      }
      let n = this.data;
      const r = this.toBBox(t);
      const o = [];
      const s = [];
      let a;
      let l;
      let u;
      while (n || o.length) {
        if (!n) {
          n = o.pop();
          l = o[o.length - 1];
          a = s.pop();
          u = true;
        }
        if (n.leaf) {
          const c = hE(t, n.children, e);
          if (c !== -1) {
            n.children.splice(c, 1);
            o.push(n);
            this._condense(o);
            return this;
          }
        }
        if (!u && !n.leaf && ls(n, r)) {
          o.push(n);
          s.push(a);
          a = 0;
          l = n;
          n = n.children[0];
        } else if (l) {
          a++;
          n = l.children[a];
          u = false;
        } else {
          n = null;
        }
      }
      return this;
    }
    toBBox(t) {
      return t;
    }
    compareMinX(t, e) {
      return t.minX - e.minX;
    }
    compareMinY(t, e) {
      return t.minY - e.minY;
    }
    toJSON() {
      return this.data;
    }
    fromJSON(t) {
      this.data = t;
      return this;
    }
    _all(t, e) {
      const n = [];
      while (t) {
        if (t.leaf) {
          e.push(...t.children);
        } else {
          n.push(...t.children);
        }
        t = n.pop();
      }
      return e;
    }
    _build(t, e, n, r) {
      const o = n - e + 1;
      let s = this._maxEntries;
      let a;
      if (o <= s) {
        a = yn(t.slice(e, n + 1));
        vn(a, this.toBBox);
        return a;
      }
      if (!r) {
        r = Math.ceil(Math.log(o) / Math.log(s));
        s = Math.ceil(o / Math.pow(s, r - 1));
      }
      a = yn([]);
      a.leaf = false;
      a.height = r;
      const l = Math.ceil(o / s);
      const u = l * Math.ceil(Math.sqrt(s));
      Th(t, e, n, u, this.compareMinX);
      for (let c = e; c <= n; c += u) {
        const h = Math.min(c + u - 1, n);
        Th(t, c, h, l, this.compareMinY);
        for (let d = c; d <= h; d += l) {
          const g = Math.min(d + l - 1, h);
          a.children.push(this._build(t, d, g, r - 1));
        }
      }
      vn(a, this.toBBox);
      return a;
    }
    _chooseSubtree(t, e, n, r) {
      while ((r.push(e), !e.leaf && r.length - 1 !== n)) {
        let o = 1 / 0;
        let s = 1 / 0;
        let a;
        for (let l = 0; l < e.children.length; l++) {
          const u = e.children[l];
          const c = as(u);
          const h = pE(t, u) - c;
          if (h < s) {
            s = h;
            o = c < o ? c : o;
            a = u;
          } else if (h === s && c < o) {
            o = c;
            a = u;
          }
        }
        e = a || e.children[0];
      }
      return e;
    }
    _insert(t, e, n) {
      const r = n ? t : this.toBBox(t);
      const o = [];
      const s = this._chooseSubtree(r, this.data, e, o);
      s.children.push(t);
      for (Gn(s, r); e >= 0 && o[e].children.length > this._maxEntries; ) {
        this._split(o, e);
        e--;
      }
      this._adjustParentBBoxes(r, o, e);
    }
    _split(t, e) {
      const n = t[e];
      const r = n.children.length;
      const o = this._minEntries;
      this._chooseSplitAxis(n, o, r);
      const s = this._chooseSplitIndex(n, o, r);
      const a = yn(n.children.splice(s, n.children.length - s));
      a.height = n.height;
      a.leaf = n.leaf;
      vn(n, this.toBBox);
      vn(a, this.toBBox);
      if (e) {
        t[e - 1].children.push(a);
      } else {
        this._splitRoot(n, a);
      }
    }
    _splitRoot(t, e) {
      this.data = yn([t, e]);
      this.data.height = t.height + 1;
      this.data.leaf = false;
      vn(this.data, this.toBBox);
    }
    _chooseSplitIndex(t, e, n) {
      let r;
      let o = 1 / 0;
      let s = 1 / 0;
      for (let a = e; a <= n - e; a++) {
        const l = jn(t, 0, a, this.toBBox);
        const u = jn(t, a, n, this.toBBox);
        const c = mE(l, u);
        const h = as(l) + as(u);
        if (c < o) {
          o = c;
          r = a;
          s = h < s ? h : s;
        } else if (c === o && h < s) {
          s = h;
          r = a;
        }
      }
      return r || n - e;
    }
    _chooseSplitAxis(t, e, n) {
      const r = t.leaf ? this.compareMinX : dE;
      const o = t.leaf ? this.compareMinY : fE;
      const s = this._allDistMargin(t, e, n, r);
      const a = this._allDistMargin(t, e, n, o);
      if (s < a) {
        t.children.sort(r);
      }
    }
    _allDistMargin(t, e, n, r) {
      t.children.sort(r);
      const o = this.toBBox;
      const s = jn(t, 0, e, o);
      const a = jn(t, n - e, n, o);
      let l = or(s) + or(a);
      for (let u = e; u < n - e; u++) {
        const c = t.children[u];
        Gn(s, t.leaf ? o(c) : c);
        l += or(s);
      }
      for (let u = n - e - 1; u >= e; u--) {
        const c = t.children[u];
        Gn(a, t.leaf ? o(c) : c);
        l += or(a);
      }
      return l;
    }
    _adjustParentBBoxes(t, e, n) {
      for (let r = n; r >= 0; r--) {
        Gn(e[r], t);
      }
    }
    _condense(t) {
      let e = t.length - 1;
      for (let n; e >= 0; e--) {
        if (t[e].children.length === 0) {
          if (e > 0) {
            n = t[e - 1].children;
            n.splice(n.indexOf(t[e]), 1);
          } else {
            this.clear();
          }
        } else {
          vn(t[e], this.toBBox);
        }
      }
    }
  }
  const Ch = (i, t) => {
    if (i.targets[0].selector.type === 'FragmentSelector') {
      const { x: n, y: r, w: o, h: s } = Mt(i);
      return { minX: n, minY: r, maxX: n + o, maxY: r + s };
    } else {
      const n = ss(i, t);
      const r = document.createElementNS(de, 'svg');
      r.style.position = 'absolute';
      r.style.opacity = 0;
      r.style.top = 0;
      r.style.left = 0;
      r.appendChild(n);
      document.body.appendChild(r);
      const { x: o, y: s, width: a, height: l } = n.getBBox();
      document.body.removeChild(r);
      return { minX: o, minY: s, maxX: o + a, maxY: s + l };
    }
  };
  const gE = (i) => {
    var e;
    const t = i.targets[0];
    if (Array.isArray(t.selector)) {
      return t.selector[0].type;
    } else if ((e = t.selector) == null) {
      return;
    } else {
      return e.type;
    }
  };
  const vE = (i, t, e) => {
    const n = Gt(e);
    const r = n.nodeName.toLowerCase();
    const o = [i, t];
    if (r === 'polygon') {
      const s = Array.from(n.points).map((a) => [a.x, a.y]);
      return ns(o, s);
    } else if (r === 'circle') {
      const s = n.getAttribute('cx');
      const a = n.getAttribute('cy');
      const l = n.getAttribute('r');
      return qS(o, s, a, l);
    } else if (r === 'ellipse') {
      const s = n.getAttribute('cx');
      const a = n.getAttribute('cy');
      const l = n.getAttribute('rx');
      const u = n.getAttribute('ry');
      return XS(o, s, a, l, u);
    } else {
      if (r === 'path') {
        return yh(n).find((a) => ns(o, a));
      }
      if (r === 'line') {
        return true;
      }
      throw `Unsupported SVG shape type: ${r}`;
    }
  };
  class yE {
    constructor(t) {
      P(this, 'clear', () => this.spatial_index.clear());
      P(this, 'getAnnotationAt', (t, e, n) => {
        const r = n ? 5 / n : 5;
        const s = this.spatial_index
          .search({ minX: t - r, minY: e - r, maxX: t + r, maxY: e + r })
          .map((a) => a.annotation)
          .filter((a) => {
            const l = gE(a);
            if (l === 'FragmentSelector') {
              return true;
            }
            if (l === 'SvgSelector') {
              return vE(t, e, a);
            }
            throw `Unsupported selector type: ${l}`;
          });
        if (s.length > 0) {
          s.sort((a, l) => _h(a, this.env.image) - _h(l, this.env.image));
          return s[0];
        }
      });
      P(this, 'getAnnotationsIntersecting', (t) =>
        this.spatial_index.search(t).map((e) => e.annotation)
      );
      P(this, 'insert', (t) => {
        (Array.isArray(t) ? t : [t]).forEach((n) => {
          this.spatial_index.insert(
            je(ue({}, Ch(n, this.env.image)), { annotation: n })
          );
        });
      });
      P(this, 'remove', (t) => {
        const e = je(ue({}, Ch(t, this.env.image)), { annotation: t });
        this.spatial_index.remove(
          e,
          (n, r) => n.annotation.id === r.annotation.id
        );
      });
      this.env = t;
      this.spatial_index = new cE();
    }
  }
  const Ph = (i, t) => {
    const r = (Zo(t, '.a9s-annotation') ? t : t.closest('.a9s-annotation'))
      .querySelector('.a9s-outer')
      .getBoundingClientRect();
    const { canvas: o } = i.drawer;
    const s = o.getBoundingClientRect();
    const a = o.width / s.width;
    const l = o.height / s.height;
    const u = r.x - s.x;
    const c = r.y - s.y;
    const { width: h, height: d } = r;
    const g = document.createElement('CANVAS');
    const y = g.getContext('2d');
    g.width = h;
    g.height = d;
    y.drawImage(o, u * a, c * l, h * a, d * l, 0, 0, h, d);
    const x = i.viewport.viewerElementToImageCoordinates(
      new OpenSeadragon.Point(u, c)
    );
    const b = i.viewport.viewportToImageZoom(i.viewport.getZoom());
    return {
      snippet: g,
      transform: (T) => {
        const f = x.x + T[0] / a / b;
        const E = x.y + T[1] / l / b;
        return [f, E];
      },
    };
  };
  const wE = $o();
  class Ah extends Qn {
    constructor(t) {
      super();
      P(this, '_getShapeAt', (t) => {
        const e = (s) => {
          const a = this.svg.createSVGPoint();
          if (window.TouchEvent && s instanceof TouchEvent) {
            const l = this.svg.getBoundingClientRect();
            const u = s.touches[0];
            const c = u.clientX - l.x;
            const h = u.clientY - l.y;
            const { left: d, top: g } = this.svg.getBoundingClientRect();
            a.x = c + d;
            a.y = h + g;
            return a.matrixTransform(this.g.getScreenCTM().inverse());
          } else {
            a.x = s.offsetX;
            a.y = s.offsetY;
            return a.matrixTransform(this.g.getCTM().inverse());
          }
        };
        const { x: n, y: r } = e(t);
        const o = this.store.getAnnotationAt(n, r, this.currentScale());
        if (o) {
          return this.findShape(o);
        }
      });
      P(this, '_initDrawingTools', (t) => {
        var o;
        this.tools = new rE(this.g, this.config, this.env);
        this.tools.on('complete', this.onDrawingComplete);
        let e = false;
        this.mouseTracker = new Rt.MouseTracker({
          element: this.svg,
          preProcessEventHandler: (s) => {
            if (!this.mouseTracker.enabled) {
              s.preventDefault = false;
              s.preventGesture = true;
            }
            if (this.selectedShape && s.eventType === 'wheel') {
              s.preventDefault = false;
              this.viewer.canvas.dispatchEvent(
                new s.originalEvent.constructor(s.eventType, s.originalEvent)
              );
            }
          },
          pressHandler: (s) => {
            if (!this.tools.current.isDrawing) {
              this.tools.current.start(
                s.originalEvent,
                this.drawOnSingleClick && !this.hoveredShape
              );
              if (!t) {
                this.scaleTool(this.tools.current);
              }
            }
          },
          moveHandler: (s) => {
            if (this.tools.current.isDrawing) {
              s.originalEvent.stopPropagation();
              const { x: a, y: l } = this.tools.current.getSVGPoint(
                s.originalEvent
              );
              this.tools.current.onMouseMove(a, l, s.originalEvent);
              if (!e) {
                this.emit('startSelection', { x: a, y: l });
                e = true;
              }
            }
          },
          releaseHandler: (s) => {
            if (this.tools.current.isDrawing) {
              const { x: a, y: l } = this.tools.current.getSVGPoint(
                s.originalEvent
              );
              this.tools.current.onMouseUp(a, l, s.originalEvent);
            }
            e = false;
          },
        });
        const n = this.config.hotkey
          ? this.config.hotkey.key
            ? this.config.hotkey.key.toLowerCase()
            : this.config.hotkey.toLowerCase()
          : 'shift';
        const r = (o = this.config.hotkey) == null ? void 0 : o.inverted;
        this.mouseTracker.enabled = r;
        if (this.onKeyDown) {
          document.removeEventListener('keydown', this.onKeyDown);
        }
        if (this.onKeyUp) {
          document.removeEventListener('keydown', this.onKeyDown);
        }
        this.onKeyDown = (s) => {
          if (s.key.toLowerCase() === n && !this.selectedShape) {
            this.mouseTracker.enabled = !this.readOnly && !r;
          }
        };
        this.onKeyUp = (s) => {
          if (s.key.toLowerCase() === n && !this.tools.current.isDrawing) {
            this.mouseTracker.enabled = r;
          }
        };
        document.addEventListener('keydown', this.onKeyDown);
        document.addEventListener('keyup', this.onKeyUp);
      });
      P(this, '_initMouseEvents', () => {
        this.svg.addEventListener('mousemove', (e) => {
          var n;
          var r;
          if (
            !((n = this.tools) == null ? void 0 : n.current.isDrawing) &&
            !e.target.closest('.a9s-annotation.editable.selected')
          ) {
            const s = this._getShapeAt(e);
            if (
              (s == null ? void 0 : s.annotation) !==
              ((r = this.hoveredShape) == null ? void 0 : r.annotation)
            ) {
              if (this.hoveredShape) {
                const a = this.hoveredShape.element || this.hoveredShape;
                Yo(a, 'hover');
                this.emit(
                  'mouseLeaveAnnotation',
                  this.hoveredShape.annotation,
                  this.hoveredShape
                );
              }
              if (s) {
                _t(s, 'hover');
                this.emit('mouseEnterAnnotation', s.annotation, s);
              }
            }
            this.hoveredShape = s;
          }
        });
        let t = null;
        this.viewer.addHandler(
          'canvas-press',
          () => (t = new Date().getTime())
        );
        this.viewer.addHandler('canvas-click', (e) => {
          var r;
          const { originalEvent: n } = e;
          if (
            !((r = this.tools.current) == null ? void 0 : r.isDrawing) &&
            !this.disableSelect &&
            new Date().getTime() - t < 250
          ) {
            const a = n.target.closest('.a9s-annotation.editable.selected')
              ? this.selectedShape
              : this._getShapeAt(n);
            if (a) {
              e.preventDefaultAction = true;
              this.selectShape(a);
            } else if (!a) {
              this.deselect();
              this.emit('select', {});
            }
          }
          if (this.disableSelect) {
            this.emit(
              'clickAnnotation',
              this.hoveredShape.annotation,
              this.hoveredShape
            );
          }
        });
      });
      P(this, '_lazy', (t) => {
        if (this.viewer.world.getItemAt(0)) {
          t();
        } else {
          const e = () => {
            t();
            this.viewer.removeHandler('open', e);
            this.viewer.world.removeHandler('add-item', e);
          };
          this.viewer.addHandler('open', e);
          this.viewer.world.addHandler('add-item', e);
        }
      });
      P(this, '_refreshNonScalingAnnotations', () => {
        const t = this.currentScale();
        Array.from(this.svg.querySelectorAll('.a9s-non-scaling')).forEach((e) =>
          e.setAttribute('transform', `scale(${1 / t})`)
        );
      });
      P(this, 'addAnnotation', (t, e) => {
        const n = e || this.g;
        const r = ss(t, this.env.image);
        _t(r, 'a9s-annotation');
        r.setAttribute('data-id', t.id);
        r.annotation = t;
        n.appendChild(r);
        nr(r, t, this.formatters);
        this.scaleFormatterElements(r);
        return r;
      });
      P(this, 'addDrawingTool', (t) => this.tools.registerTool(t));
      P(this, 'addOrUpdateAnnotation', (t, e) => {
        var r;
        var o;
        if (
          ((r = this.selectedShape) == null ? void 0 : r.annotation) === t ||
          ((o = this.selectedShape) == null ? void 0 : o.annotation) == e
        ) {
          this.deselect();
        }
        if (e) {
          this.removeAnnotation(t);
        }
        this.removeAnnotation(t);
        const n = this.addAnnotation(t);
        if (Zo(n, 'a9s-non-scaling')) {
          n.setAttribute('transform', `scale(${1 / this.currentScale()})`);
        }
        this.store.insert(t);
      });
      P(this, 'currentScale', () => {
        const t = this.viewer.viewport.getContainerSize().x;
        return (
          (this.viewer.viewport.getZoom(true) * t) /
          this.viewer.world.getContentFactor()
        );
      });
      P(this, 'deselect', () => {
        var t;
        if ((t = this.tools) != null) {
          t.current.stop();
        }
        if (this.selectedShape) {
          const { annotation: e } = this.selectedShape;
          if (this.selectedShape.destroy) {
            this.selectedShape.mouseTracker.destroy();
            this.selectedShape.destroy();
            if (!e.isSelection) {
              const n = this.addAnnotation(e);
              if (Zo(n, 'a9s-non-scaling')) {
                n.setAttribute(
                  'transform',
                  `scale(${1 / this.currentScale()})`
                );
              }
            }
          } else {
            Yo(this.selectedShape, 'selected');
          }
          this.selectedShape = null;
        }
      });
      P(this, 'destroy', () => {
        this.deselect();
        this.svg.parentNode.removeChild(this.svg);
      });
      P(this, 'findShape', (t) => {
        const e = (t == null ? void 0 : t.id) ? t.id : t;
        return this.g.querySelector(`.a9s-annotation[data-id="${e}"]`);
      });
      P(this, '_fit', (t, e, n) => {
        const r = this.findShape(t);
        if (r) {
          const { x: o, y: s, width: a, height: l } = r.getBBox();
          const u = this.viewer.viewport.imageToViewportRectangle(o, s, a, l);
          this.viewer.viewport[n](u, e);
        }
      });
      P(this, 'fitBounds', (t, e) => this._fit(t, e, 'fitBounds'));
      P(this, 'fitBoundsWithConstraints', (t, e) =>
        this._fit(t, e, 'fitBoundsWithConstraints')
      );
      P(this, 'getAnnotations', () =>
        Array.from(this.g.querySelectorAll('.a9s-annotation')).map(
          (e) => e.annotation
        )
      );
      P(this, 'getImageSnippetById', (t) => {
        const e = this.findShape(t);
        if (e) {
          return Ph(this.viewer, e);
        }
      });
      P(this, 'getSelectedImageSnippet', () => {
        var t;
        if (this.selectedShape) {
          const e =
            (t = this.selectedShape.element) != null ? t : this.selectedShape;
          return Ph(this.viewer, e);
        }
      });
      P(this, 'init', (t) => {
        this.deselect();
        Array.from(this.g.querySelectorAll('.a9s-annotation')).forEach((n) =>
          this.g.removeChild(n)
        );
        this.store.clear();
        this._lazy(() => {
          console.time('Took');
          console.log('Drawing...');
          if (!this.loaded) {
            this.g.style.display = 'none';
          }
          t.forEach((n) => this.addAnnotation(n));
          console.log('Indexing...');
          this.store.insert(t);
          console.timeEnd('Took');
          this.resize();
        });
      });
      P(this, 'listDrawingTools', () => this.tools.listTools());
      P(this, 'overrideId', (t, e) => {
        const n = this.findShape(t);
        n.setAttribute('data-id', e);
        const { annotation: r } = n;
        const o = r.clone({ id: e });
        n.annotation = o;
        this.store.remove(r);
        this.store.insert(o);
        return o;
      });
      P(this, 'panTo', (t, e) => {
        const n = this.findShape(t);
        if (n) {
          const {
            top: r,
            left: o,
            width: s,
            height: a,
          } = n.getBoundingClientRect();
          const l = o + s / 2 + window.scrollX;
          const u = r + a / 2 + window.scrollY;
          const c = this.viewer.viewport.windowToViewportCoordinates(
            new Rt.Point(l, u)
          );
          this.viewer.viewport.panTo(c, e);
        }
      });
      P(this, 'removeAnnotation', (t) => {
        var r;
        var o;
        const e = t.type ? t.id : t;
        if (
          ((r = this.selectedShape) == null ? void 0 : r.annotation.id) === e
        ) {
          this.deselect();
        }
        const n = this.findShape(t);
        if (n) {
          const { annotation: s } = n;
          if (
            ((o = this.selectedShape) == null ? void 0 : o.annotation) === s
          ) {
            this.deselect();
          }
          n.parentNode.removeChild(n);
          this.store.remove(s);
        }
      });
      P(this, 'removeDrawingTool', (t) => {
        var e;
        if ((e = this.tools) == null) {
          return;
        } else {
          return e.unregisterTool(t);
        }
      });
      P(this, 'scaleFormatterElements', (t) => {
        const e = 1 / this.currentScale();
        if (t) {
          const n = t.querySelector('.a9s-formatter-el');
          if (n) {
            n.firstChild.setAttribute('transform', `scale(${e})`);
          }
        } else {
          Array.from(this.g.querySelectorAll('.a9s-formatter-el')).forEach(
            (r) => r.firstChild.setAttribute('transform', `scale(${e})`)
          );
        }
      });
      P(this, 'scaleTool', (t) => {
        if (t) {
          const e = 1 / this.currentScale();
          t.scale = e;
          if (t.onScaleChanged) {
            t.onScaleChanged(e);
          }
        }
      });
      P(this, 'selectAnnotation', (t, e) => {
        if (this.selectedShape) {
          this.deselect();
        }
        const n = this.findShape(t);
        if (n) {
          this.selectShape(n, e);
          const r = this.selectedShape.element
            ? this.selectedShape.element
            : this.selectedShape;
          return { annotation: n.annotation, element: r };
        } else {
          this.deselect();
        }
      });
      P(this, 'selectShape', (t, e) => {
        var o;
        if (!e && !t.annotation.isSelection) {
          this.emit('clickAnnotation', t.annotation, t);
        }
        if (
          ((o = this.selectedShape) == null ? void 0 : o.annotation) ===
          t.annotation
        ) {
          return;
        }
        if (
          this.selectedShape &&
          this.selectedShape.annotation !== t.annotation
        ) {
          this.deselect();
        }
        const { annotation: n } = t;
        if (this.readOnly || n.readOnly || this.headless) {
          this.selectedShape = t;
          _t(t, 'selected');
          if (!e) {
            this.emit('select', { annotation: n, element: t, skipEvent: e });
          }
        } else {
          const s = this.tools.forAnnotation(n);
          if (s) {
            setTimeout(() => {
              t.parentNode.removeChild(t);
              if (!e) {
                this.emit('select', {
                  annotation: n,
                  element: this.selectedShape.element,
                });
              }
            }, 1);
            this.selectedShape = s.createEditableShape(n, this.formatters);
            this.scaleTool(this.selectedShape);
            this.scaleFormatterElements(this.selectedShape.element);
            this.selectedShape.element.annotation = n;
            const a = new Rt.MouseTracker({
              element: this.svg,
              preProcessEventHandler: (l) => {
                l.stopPropagation = true;
                l.preventDefault = false;
                l.preventGesture = true;
              },
            });
            this.selectedShape.element.addEventListener('mouseenter', () => {
              this.hoveredShape = this.selectedShape;
              a.setTracking(true);
            });
            this.selectedShape.element.addEventListener('mouseleave', () => {
              this.hoveredShape = null;
              a.setTracking(false);
            });
            this.selectedShape.mouseTracker = a;
            this.selectedShape.on('update', (l) =>
              this.emit('updateTarget', this.selectedShape.element, l)
            );
          } else {
            this.selectedShape = t;
            if (!e) {
              this.emit('select', {
                annotation: n,
                element: this.selectedShape,
              });
            }
          }
        }
      });
      P(this, 'setDrawingEnabled', (t) => {
        if (this.mouseTracker) {
          const e = t && !this.readOnly;
          this.mouseTracker.enabled = e;
          this.mouseTracker.setTracking(e);
        }
      });
      P(this, 'setDrawingTool', (t) => {
        var e;
        if (this.tools) {
          if ((e = this.tools.current) != null) {
            e.stop();
          }
          this.tools.setCurrent(t);
        }
      });
      P(this, 'setVisible', (t) => {
        if (t) {
          this.svg.style.display = null;
        } else {
          this.deselect();
          this.svg.style.display = 'none';
        }
      });
      P(this, 'stopDrawing', () => {
        var t;
        var e;
        if ((e = (t = this.tools) == null ? void 0 : t.current) == null) {
          return;
        } else {
          return e.stop();
        }
      });
      this.viewer = t.viewer;
      this.config = t.config;
      this.env = t.env;
      this.readOnly = t.config.readOnly;
      this.headless = t.config.headless;
      if (t.config.formatter) {
        this.formatters = [t.config.formatter];
      } else if (t.config.formatters) {
        this.formatters = Array.isArray(t.config.formatters)
          ? t.config.formatters
          : [t.config.formatters];
      }
      this.disableSelect = t.disableSelect;
      this.drawOnSingleClick = t.config.drawOnSingleClick;
      this.svg = document.createElementNS(de, 'svg');
      if (wE) {
        this.svg.setAttribute(
          'class',
          'a9s-annotationlayer a9s-osd-annotationlayer touch'
        );
        VS(this.svg);
      } else {
        this.svg.setAttribute(
          'class',
          'a9s-annotationlayer a9s-osd-annotationlayer'
        );
      }
      this.g = document.createElementNS(de, 'g');
      this.svg.appendChild(this.g);
      this.viewer.canvas.appendChild(this.svg);
      this.viewer.addHandler('animation', () => this.resize());
      this.viewer.addHandler('rotate', () => this.resize());
      this.viewer.addHandler('resize', () => this.resize());
      this.viewer.addHandler('flip', () => this.resize());
      this.loaded = false;
      const e = () => {
        const { x: n, y: r } = this.viewer.world.getItemAt(0).source.dimensions;
        const o =
          this.viewer.world.getItemAt(0).source['@id'] ||
          new URL(this.viewer.world.getItemAt(0).source.url, document.baseURI)
            .href;
        this.env.image = { src: o, naturalWidth: n, naturalHeight: r };
        if (t.config.crosshair) {
          this.crosshair = new oE(this.g, n, r);
          _t(this.svg, 'has-crosshair');
        }
        if (!this.loaded) {
          this.emit('load', o);
        }
        this.loaded = true;
        this.g.style.display = 'inline';
        this.resize();
      };
      this.viewer.addHandler('open', e);
      this.viewer.world.addHandler('add-item', e);
      if (this.viewer.world.getItemAt(0)) {
        e();
      }
      this.store = new yE(this.env);
      this.selectedShape = null;
      this.hoveredShape = null;
      this._initMouseEvents();
    }
    resize() {
      var s;
      const t = this.viewer.viewport.getFlip();
      const e = this.viewer.viewport.pixelFromPoint(new Rt.Point(0, 0), true);
      if (t) {
        e.x = this.viewer.viewport._containerInnerSize.x - e.x;
      }
      const n = this.currentScale();
      const r = t ? -n : n;
      const o = this.viewer.viewport.getRotation();
      this.g.setAttribute(
        'transform',
        `translate(${e.x}, ${e.y}) scale(${r}, ${n}) rotate(${o})`
      );
      this._refreshNonScalingAnnotations();
      this.scaleFormatterElements();
      if (this.selectedShape) {
        if (this.selectedShape.element) {
          this.scaleTool(this.selectedShape);
          this.emit('viewportChange', this.selectedShape.element);
        } else {
          this.emit('viewportChange', this.selectedShape);
        }
      }
      if ((s = this.tools) == null ? void 0 : s.current.isDrawing) {
        this.scaleTool(this.tools.current);
      }
    }
  }
  class bE extends Ah {
    constructor(t) {
      super(t);
      P(this, 'onDrawingComplete', (t) => {
        var e;
        this.mouseTracker.enabled =
          (e = this.config.hotkey) == null ? void 0 : e.inverted;
        this.selectShape(t);
        this.emit('createSelection', t.annotation);
      });
      this._initDrawingTools();
    }
  }
  const us = (i) => {
    const t = i.viewport.viewportToImageRectangle(i.viewport.getBounds(true));
    const e = i.viewport.getContainerSize().x;
    const r = (i.viewport.getZoom(true) * e) / i.world.getContentFactor();
    return { extent: t, scale: r };
  };
  const SE = (i) => {
    var e;
    const t = i.targets[0];
    if (t) {
      if (Array.isArray(t.selector)) {
        return t.selector[0].type;
      } else if ((e = t.selector) == null) {
        return;
      } else {
        return e.type;
      }
    } else {
      return null;
    }
  };
  const Oh = (i, t) => {
    const { extent: e, scale: n } = us(i);
    const { selector: r } = t;
    const o = Dt.create({ target: t });
    if (r.type === 'SvgSelector') {
      const s = Gt(o);
      const a = s.nodeName.toLowerCase();
      let l = null;
      if (a === 'polygon') {
        l = EE(s, e, n);
      } else if (a === 'circle') {
        l = _E(s, e, n);
      } else if (a === 'ellipse') {
        l = xE(s, e, n);
      } else if (a === 'path') {
        l = TE(s, e, n);
      } else {
        throw `Unsupported SVG shape type: ${a}`;
      }
      let u = l.outerHTML || new XMLSerializer().serializeToString(l);
      u = u.replace(` xmlns="${de}"`, '');
      return je(ue({}, t), {
        selector: { type: 'SvgSelector', value: `<svg>${u}</svg>` },
      });
    } else if (r.type === 'FragmentSelector') {
      const { x: s, y: a, w: l, h: u } = Mt(o);
      const c = e.x + s / n;
      const h = e.y + a / n;
      const d = l / n;
      const g = u / n;
      if (l === 0 && u === 0) {
        return je(ue({}, gn(c, h, d, g)), { renderedVia: { name: 'point' } });
      } else {
        return gn(c, h, d, g);
      }
    } else {
      throw `Unsupported selector type: ${r.type}`;
    }
  };
  const EE = (i, t, e) => {
    const r = Array.from(i.points)
      .map((o) => {
        const s = t.x + o.x / e;
        const a = t.y + o.y / e;
        return s + ',' + a;
      })
      .join(' ');
    i.setAttribute('points', r);
    return i;
  };
  const _E = (i, t, e) => {
    const n = parseFloat(i.getAttribute('cx'));
    const r = parseFloat(i.getAttribute('cy'));
    const o = parseFloat(i.getAttribute('r'));
    i.setAttribute('cx', t.x + n / e);
    i.setAttribute('cy', t.y + r / e);
    i.setAttribute('r', o / e);
    return i;
  };
  const xE = (i, t, e) => {
    const n = parseFloat(i.getAttribute('cx'));
    const r = parseFloat(i.getAttribute('cy'));
    const o = parseFloat(i.getAttribute('rx'));
    const s = parseFloat(i.getAttribute('ry'));
    i.setAttribute('cx', t.x + n / e);
    i.setAttribute('cy', t.y + r / e);
    i.setAttribute('rx', o / e);
    i.setAttribute('ry', s / e);
    return i;
  };
  const TE = (i, t, e) => {
    const r = i
      .getAttribute('d')
      .split(/(?=M|m|L|l|H|h|V|v|Z|z)/g)
      .map((o) => o.trim())
      .map((o) => {
        const s = o.substring(0, 1);
        if (s.toLowerCase() === 'z') {
          return s;
        }
        {
          const a = o
            .substring(1)
            .split(' ')
            .map((h) => parseFloat(h.trim()));
          const l = s === s.toUpperCase();
          const u = l ? t.x + a[0] / e : a[0] / e;
          const c = l ? t.y + a[1] / e : a[1] / e;
          return s + ' ' + u + ' ' + c;
        }
      })
      .join(' ');
    i.setAttribute('d', r);
    return i;
  };
  const Dh = (i, t) => {
    const { extent: e, scale: n } = us(i);
    const r = t.selector('FragmentSelector');
    if (t.selector('SvgSelector')) {
      const s = Gt(t);
      const a = s.nodeName.toLowerCase();
      let l = null;
      if (a === 'polygon') {
        l = CE(s, e, n);
      } else if (a === 'circle') {
        l = PE(s, e, n);
      } else if (a === 'ellipse') {
        l = AE(s, e, n);
      } else if (a === 'path') {
        l = OE(s, e, n);
      } else {
        throw `Unsupported SVG shape type: ${a}`;
      }
      let u = l.outerHTML || new XMLSerializer().serializeToString(l);
      u = u.replace(` xmlns="${de}"`, '');
      const c = { selector: { type: 'SvgSelector', value: `<svg>${u}</svg>` } };
      return t.clone({ target: c });
    } else if (r) {
      const { x: s, y: a, w: l, h: u } = Mt(t);
      const c = (s - e.x) * n;
      const h = (a - e.y) * n;
      const d = gn(c, h, l * n, u * n);
      return t.clone({ target: d });
    }
  };
  const CE = (i, t, e) => {
    const r = Array.from(i.points)
      .map((o) => {
        const s = e * (o.x - t.x);
        const a = e * (o.y - t.y);
        return s + ',' + a;
      })
      .join(' ');
    i.setAttribute('points', r);
    return i;
  };
  const PE = (i, t, e) => {
    const n = i.getAttribute('cx');
    const r = i.getAttribute('cy');
    const o = i.getAttribute('r');
    i.setAttribute('cx', e * (n - t.x));
    i.setAttribute('cy', e * (r - t.y));
    i.setAttribute('r', o * e);
    return i;
  };
  const AE = (i, t, e) => {
    const n = i.getAttribute('cx');
    const r = i.getAttribute('cy');
    const o = i.getAttribute('rx');
    const s = i.getAttribute('ry');
    i.setAttribute('cx', e * (n - t.x));
    i.setAttribute('cy', e * (r - t.y));
    i.setAttribute('rx', o * e);
    i.setAttribute('ry', s * e);
    return i;
  };
  const OE = (i, t, e) => {
    const r = i
      .getAttribute('d')
      .split(/(?=M|m|L|l|H|h|V|v|Z|z)/g)
      .map((o) => o.trim())
      .map((o) => {
        const s = o.substring(0, 1);
        if (s.toLowerCase() === 'z') {
          return s;
        }
        {
          const a = o
            .substring(1)
            .split(' ')
            .filter((h) => h)
            .map((h) => parseFloat(h.trim()));
          const l = s === s.toUpperCase();
          const u = l ? e * (a[0] - t.x) : e * a[0];
          const c = l ? e * (a[1] - t.y) : e * a[1];
          return s + ' ' + u + ' ' + c;
        }
      })
      .join(' ');
    i.setAttribute('d', r);
    return i;
  };
  const Rh = (i, t) => {
    const { extent: e, scale: n } = us(i);
    const r = SE(t.annotation);
    if (r === 'FragmentSelector') {
      DE(t, e, n);
    } else if (r === 'SvgSelector') {
      RE(t, e, n);
    } else {
      throw `Unsupported selector type type: ${r}`;
    }
    const o = t.querySelector('.a9s-formatter-el');
    if (o) {
      const { x: s, y: a } = t.querySelector('.a9s-inner').getBBox();
      o.setAttribute('x', s);
      o.setAttribute('y', a);
    }
  };
  const DE = (i, t, e) => {
    const { x: n, y: r, w: o, h: s } = Mt(i.annotation);
    const a = i.querySelector('.a9s-outer');
    const l = i.querySelector('.a9s-inner');
    const u = e * (n - t.x);
    const c = e * (r - t.y);
    if (o === 0 && s === 0) {
      [a, l].forEach((h) => {
        h.setAttribute('cx', u);
        h.setAttribute('cy', c);
      });
    } else {
      [a, l].forEach((h) => {
        h.setAttribute('x', u);
        h.setAttribute('y', c);
        h.setAttribute('width', o * e);
        h.setAttribute('height', s * e);
      });
    }
  };
  const RE = (i, t, e) => {
    const n = Gt(i.annotation);
    const r = n.nodeName.toLowerCase();
    if (r === 'polygon') {
      ME(i, n, t, e);
    } else if (r === 'circle') {
      FE(i, n, t, e);
    } else if (r === 'ellipse') {
      LE(i, n, t, e);
    } else if (r === 'path') {
      kE(i, n, t, e);
    } else {
      throw `Unsupported SVG shape type: ${r}`;
    }
  };
  const ME = (i, t, e, n) => {
    const o = Array.from(t.points)
      .map((l) => {
        const u = n * (l.x - e.x);
        const c = n * (l.y - e.y);
        return u + ',' + c;
      })
      .join(' ');
    i.querySelector('.a9s-outer').setAttribute('points', o);
    i.querySelector('.a9s-inner').setAttribute('points', o);
  };
  const FE = (i, t, e, n) => {
    const r = n * (t.getAttribute('cx') - e.x);
    const o = n * (t.getAttribute('cy') - e.y);
    const s = n * t.getAttribute('r');
    const a = i.querySelector('.a9s-outer');
    a.setAttribute('cx', r);
    a.setAttribute('cy', o);
    a.setAttribute('r', s);
    const l = i.querySelector('.a9s-inner');
    l.setAttribute('cx', r);
    l.setAttribute('cy', o);
    l.setAttribute('r', s);
  };
  const LE = (i, t, e, n) => {
    const r = n * (t.getAttribute('cx') - e.x);
    const o = n * (t.getAttribute('cy') - e.y);
    const s = n * t.getAttribute('rx');
    const a = n * t.getAttribute('ry');
    const l = i.querySelector('.a9s-outer');
    l.setAttribute('cx', r);
    l.setAttribute('cy', o);
    l.setAttribute('rx', s);
    l.setAttribute('ry', a);
    const u = i.querySelector('.a9s-inner');
    u.setAttribute('cx', r);
    u.setAttribute('cy', o);
    u.setAttribute('rx', s);
    u.setAttribute('ry', a);
  };
  const kE = (i, t, e, n) => {
    const o = t
      .getAttribute('d')
      .split(/(?=M|m|L|l|H|h|V|v|Z|z)/g)
      .map((s) => s.trim())
      .map((s) => {
        const a = s.substring(0, 1);
        if (a.toLowerCase() === 'z') {
          return a;
        }
        {
          const l = s
            .substring(1)
            .split(' ')
            .filter((d) => d)
            .map((d) => parseFloat(d.trim()));
          const u = a === a.toUpperCase();
          const c = u ? n * (l[0] - e.x) : n * l[0];
          const h = u ? n * (l[1] - e.y) : n * l[1];
          return a + ' ' + c + ' ' + h;
        }
      })
      .join(' ');
    i.querySelector('.a9s-inner').setAttribute('d', o);
    i.querySelector('.a9s-outer').setAttribute('d', o);
  };
  const BE = $o();
  class NE extends Ah {
    constructor(t) {
      super(t);
      P(this, '_getShapeAt', (t) => {
        const e = (a) => {
          if (BE) {
            const l = this.svg.getBoundingClientRect();
            const u = a.clientX - l.x;
            const c = a.clientY - l.y;
            return new Rt.Point(u, c);
          } else {
            return new Rt.Point(a.offsetX, a.offsetY);
          }
        };
        const n = this.viewer.viewport.viewerElementToViewportCoordinates(e(t));
        const { x: r, y: o } = this.viewer.viewport.viewportToImageCoordinates(
          n.x,
          n.y
        );
        const s = this.store.getAnnotationAt(r, o, this.currentScale());
        if (s) {
          return this.findShape(s);
        }
      });
      P(this, '_refreshNonScalingAnnotations', () => {});
      P(this, 'addAnnotation', (t, e) => {
        const n = e || this.g;
        const r = ss(t, this.env.image);
        _t(r, 'a9s-annotation');
        r.setAttribute('data-id', t.id);
        r.annotation = t;
        Rh(this.viewer, r);
        n.appendChild(r);
        nr(r, t, this.formatters);
        return r;
      });
      P(this, 'addOrUpdateAnnotation', (t, e) => {
        var n;
        var r;
        if (
          ((n = this.selectedShape) == null ? void 0 : n.annotation) === t ||
          ((r = this.selectedShape) == null ? void 0 : r.annotation) == e
        ) {
          this.deselect();
        }
        if (e) {
          this.removeAnnotation(t);
        }
        this.removeAnnotation(t);
        this.addAnnotation(t);
        this.store.insert(t);
      });
      P(this, 'deselect', () => {
        var t;
        if ((t = this.tools) != null) {
          t.current.stop();
        }
        if (this.selectedShape) {
          const { annotation: e } = this.selectedShape;
          if (this.selectedShape.destroy) {
            this.selectedShape.mouseTracker.destroy();
            this.selectedShape.destroy();
            if (!e.isSelection) {
              this.addAnnotation(e);
            }
          } else {
            Yo(this.selectedShape, 'selected');
          }
          this.selectedShape = null;
        }
      });
      P(this, 'onDrawingComplete', (t) => {
        const e = t.annotation.clone({
          target: Oh(this.viewer, t.annotation.target),
        });
        t.annotation = e;
        this.selectShape(t);
        this.emit('createSelection', t.annotation);
        this.mouseTracker.enabled = false;
      });
      P(this, 'selectShape', (t, e) => {
        var o;
        if (!e && !t.annotation.isSelection) {
          this.emit('clickAnnotation', t.annotation, t);
        }
        if (
          ((o = this.selectedShape) == null ? void 0 : o.annotation) ===
          t.annotation
        ) {
          return;
        }
        if (
          this.selectedShape &&
          this.selectedShape.annotation !== t.annotation
        ) {
          this.deselect(true);
        }
        const { annotation: n } = t;
        if (this.readOnly || n.readOnly || this.headless) {
          this.selectedShape = t;
          _t(t, 'selected');
          if (!e) {
            this.emit('select', { annotation: n, element: t, skipEvent: e });
          }
        } else {
          setTimeout(() => {
            t.parentNode.removeChild(t);
            if (!e) {
              this.emit('select', {
                annotation: n,
                element: this.selectedShape.element,
              });
            }
          }, 1);
          const s = this.tools.forAnnotation(n);
          this.selectedShape = s.createEditableShape(n);
          this.selectedShape.element.annotation = n;
          const a = Dh(this.viewer, n);
          this.selectedShape.updateState(a);
          const l = new Rt.MouseTracker({
            element: this.svg,
            preProcessEventHandler: (u) => {
              u.stopPropagation = true;
              u.preventDefault = false;
              u.preventGesture = true;
            },
          });
          this.selectedShape.element.addEventListener('mouseenter', () => {
            this.hoveredShape = this.selectedShape;
            l.setTracking(true);
          });
          this.selectedShape.element.addEventListener('mouseleave', () => {
            this.hoveredShape = null;
            l.setTracking(false);
          });
          this.selectedShape.mouseTracker = l;
          this.selectedShape.on('update', (u) => {
            const c = Oh(this.viewer, u);
            this.selectedShape.element.annotation =
              this.selectedShape.annotation.clone({ target: c });
            this.emit('updateTarget', this.selectedShape.element, c);
          });
        }
      });
      this._initDrawingTools(true);
    }
    resize() {
      if (!this.store) {
        return;
      }
      const t = this.viewer.viewport.getBounds(true);
      const e = new Rt.Rect(
        t.x - t.width / 10,
        t.y - t.height / 10,
        t.width * 1.2,
        t.height * 1.2
      );
      const {
        x: n,
        y: r,
        width: o,
        height: s,
      } = this.viewer.viewport.viewportToImageRectangle(e);
      const a = { minX: n, minY: r, maxX: n + o, maxY: r + s };
      const l = new Set(
        this.store.getAnnotationsIntersecting(a).map((u) => u.id)
      );
      if (l.size > 0) {
        Array.from(
          this.g.querySelectorAll('.a9s-annotation:not(.selected)')
        ).forEach((c) => {
          if (l.has(c.annotation.id)) {
            c.removeAttribute('visibility');
            Rh(this.viewer, c);
          } else if (!c.hasAttribute('visibility')) {
            c.setAttribute('visibility', 'hidden');
          }
        });
      }
      if (this.selectedShape) {
        if (this.selectedShape.element) {
          const u = Dh(this.viewer, this.selectedShape.element.annotation);
          if (this.selectedShape.updateState) {
            this.selectedShape.updateState(u);
          }
          this.emit('viewportChange', this.selectedShape.element);
        } else {
          this.emit('viewportChange', this.selectedShape);
        }
      }
    }
  }
  var IE = Jn(jh);
  var Fh = IE;
  var HE = 0;
  var Mh = zE;
  const Lh = Mh;
  class VE extends Oe {
    constructor(t) {
      super(t);
      P(this, 'clearState', (t) =>
        this.setState(
          {
            selectedAnnotation: null,
            selectedDOMElement: null,
            modifiedTarget: null,
            beforeHeadlessModify: null,
          },
          t
        )
      );
      P(this, 'forwardEvent', (t, e) => {
        this.annotationLayer.on(t, (n, r) => {
          this.props[e](n.clone(), r);
        });
      });
      P(this, 'onKeyUp', (t) => {
        if (t.which === 27) {
          this.annotationLayer.stopDrawing();
          const { selectedAnnotation: e } = this.state;
          if (e) {
            this.cancelSelected();
            this.props.onCancelSelected(e);
          }
        } else if (t.which === 46) {
          const { selectedAnnotation: e } = this.state;
          if (e) {
            if (e.isSelection) {
              this.onCancelAnnotation(e);
            } else {
              this.onDeleteAnnotation(e);
            }
          }
        }
      });
      P(this, 'handleStartSelect', (t) => this.props.onSelectionStarted(t));
      P(this, 'handleSelect', (t, e) => {
        if (this.state.editorDisabled) {
          this.onHeadlessSelect(t, e);
        } else {
          this.onNormalSelect(t, e);
        }
      });
      P(this, 'onNormalSelect', (t, e) => {
        const { annotation: n, element: r } = t;
        if (n) {
          const o = () => {
            this.setState(
              {
                selectedAnnotation: n,
                selectedDOMElement: r,
                modifiedTarget: null,
              },
              () => {
                if (!e) {
                  if (n.isSelection) {
                    this.props.onSelectionCreated(n.clone());
                  } else {
                    this.props.onAnnotationSelected(n.clone(), r);
                  }
                }
              }
            );
          };
          const { selectedAnnotation: s } = this.state;
          if (s && !s.isEqual(n)) {
            this.clearState(() => {
              this.props.onCancelSelected(s);
              o();
            });
          } else {
            o();
          }
        } else {
          const { selectedAnnotation: o } = this.state;
          if (o) {
            this.clearState(() => this.props.onCancelSelected(o));
          } else {
            this.clearState();
          }
        }
      });
      P(this, 'onHeadlessSelect', (t, e) => {
        this.saveSelected().then(() => {
          this.onNormalSelect(t, e);
        });
      });
      P(this, 'handleUpdateTarget', (t, e) => {
        this.setState({ selectedDOMElement: t, modifiedTarget: e });
        const n = JSON.parse(JSON.stringify(e));
        this.props.onSelectionTargetChanged(n);
      });
      P(this, 'handleViewportChange', (t) =>
        this.setState({ selectedDOMElement: t })
      );
      P(this, 'overrideAnnotationId', (t) => (e) => {
        const { id: n } = t;
        if (this.state.selectedAnnotation) {
          this.setState(
            {
              selectedAnnotation: null,
              selectedDOMElement: null,
              modifiedTarget: null,
            },
            () => {
              this.annotationLayer.overrideId(n, e);
            }
          );
        } else {
          this.annotationLayer.overrideId(n, e);
        }
      });
      P(this, 'onCreateOrUpdateAnnotation', (t, e) => (n, r) => {
        let o = n.isSelection ? n.toAnnotation() : n;
        o = this.state.modifiedTarget
          ? o.clone({ target: this.state.modifiedTarget })
          : o.clone();
        this.clearState(() => {
          this.annotationLayer.deselect();
          this.annotationLayer.addOrUpdateAnnotation(o, r);
          if (r) {
            this.props[t](o, r.clone());
          } else {
            this.props[t](o, this.overrideAnnotationId(o));
          }
          if (e) {
            e();
          }
        });
      });
      P(this, 'onDeleteAnnotation', (t) => {
        this.clearState();
        this.annotationLayer.removeAnnotation(t);
        this.props.onAnnotationDeleted(t);
      });
      P(this, 'onCancelAnnotation', (t, e) => {
        if (!this.state.editorDisabled) {
          this.annotationLayer.deselect();
        }
        this.props.onCancelSelected(t);
        this.clearState(e);
      });
      P(this, 'addAnnotation', (t) => {
        var e;
        if (
          t.id === ((e = this.state.selectedAnnotation) == null ? void 0 : e.id)
        ) {
          this.annotationLayer.deselect();
          this.clearState();
        }
        this.annotationLayer.addOrUpdateAnnotation(t.clone());
      });
      P(this, 'addDrawingTool', (t) => this.annotationLayer.addDrawingTool(t));
      P(
        this,
        'cancelSelected',
        () =>
          new Promise((t) => {
            this.annotationLayer.deselect();
            if (this.state.selectedAnnotation) {
              this.clearState(t);
            } else {
              t();
            }
          })
      );
      P(this, 'fitBounds', (t, e) => this.annotationLayer.fitBounds(t, e));
      P(this, 'fitBoundsWithConstraints', (t, e) =>
        this.annotationLayer.fitBoundsWithConstraints(t, e)
      );
      P(this, 'getAnnotationById', (t) => {
        var e;
        if ((e = this.annotationLayer.findShape(t)) == null) {
          return;
        } else {
          return e.annotation;
        }
      });
      P(this, 'getAnnotations', () =>
        this.annotationLayer.getAnnotations().map((t) => t.clone())
      );
      P(this, 'getImageSnippetById', (t) =>
        this.annotationLayer.getImageSnippetById(t)
      );
      P(this, 'getSelected', () => {
        var t;
        if (this.state.selectedAnnotation) {
          if (this.state.editorDisabled) {
            return this.state.selectedAnnotation;
          } else if ((t = this._editor.current) == null) {
            return;
          } else {
            return t.getCurrentAnnotation();
          }
        }
      });
      P(this, 'getSelectedImageSnippet', () =>
        this.annotationLayer.getSelectedImageSnippet()
      );
      P(this, 'listDrawingTools', () =>
        this.annotationLayer.listDrawingTools()
      );
      P(this, 'panTo', (t, e) => this.annotationLayer.panTo(t, e));
      P(this, 'removeAnnotation', (t) =>
        this.annotationLayer.removeAnnotation(t)
      );
      P(this, 'removeDrawingTool', (t) =>
        this.annotationLayer.removeDrawingTool(t)
      );
      P(
        this,
        'saveSelected',
        () =>
          new Promise((t) => {
            const e = this.state.selectedAnnotation;
            if (e) {
              if (this._editor.current) {
                this._editor.current.onOk();
                t();
              } else if (e.isSelection) {
                if (e.bodies.length > 0 || this.props.config.allowEmpty) {
                  this.onCreateOrUpdateAnnotation('onAnnotationCreated', t)(e);
                } else {
                  this.annotationLayer.deselect();
                  t();
                }
              } else {
                const { beforeHeadlessModify: n, modifiedTarget: r } =
                  this.state;
                if (n) {
                  this.onCreateOrUpdateAnnotation('onAnnotationUpdated', t)(
                    e,
                    n
                  );
                } else if (r) {
                  this.onCreateOrUpdateAnnotation('onAnnotationUpdated', t)(
                    e,
                    e
                  );
                } else {
                  this.onCancelAnnotation(e, t);
                }
              }
            } else {
              t();
            }
          })
      );
      P(this, 'selectAnnotation', (t) => {
        const e = this.annotationLayer.selectAnnotation(t, true);
        if (e) {
          this.handleSelect(e, true);
          return e.annotation.clone();
        }
        this.clearState();
      });
      P(this, 'setAnnotations', (t) =>
        this.annotationLayer.init(t.map((e) => e.clone()))
      );
      P(this, 'setDrawingEnabled', (t) =>
        this.annotationLayer.setDrawingEnabled(t)
      );
      P(this, 'setDrawingTool', (t) => this.annotationLayer.setDrawingTool(t));
      P(this, 'setVisible', (t) => {
        this.annotationLayer.setVisible(t);
        if (!t) {
          this.clearState();
        }
      });
      P(
        this,
        'updateSelected',
        (t, e) =>
          new Promise((n) => {
            if (this.state.selectedAnnotation) {
              if (e) {
                if (this.state.selectedAnnotation.isSelection) {
                  this.onCreateOrUpdateAnnotation('onAnnotationCreated', n)(t);
                } else {
                  this.onCreateOrUpdateAnnotation('onAnnotationUpdated', n)(
                    t,
                    this.state.selectedAnnotation
                  );
                }
              } else {
                this.setState(
                  {
                    selectedAnnotation: t,
                    beforeHeadlessModify:
                      this.state.beforeHeadlessModify ||
                      this.state.selectedAnnotation,
                  },
                  n
                );
              }
            }
          })
      );
      this.state = {
        selectedAnnotation: null,
        selectedDOMElement: null,
        modifiedTarget: null,
        readOnly: t.config.readOnly,
        editorDisabled: t.config.disableEditor,
        widgets: t.config.widgets,
        beforeHeadlessModify: null,
      };
      this._editor = L.createRef();
    }
    componentDidMount() {
      this.annotationLayer = this.props.config.gigapixelMode
        ? new NE(this.props)
        : new bE(this.props);
      this.annotationLayer.on('load', this.props.onLoad);
      this.annotationLayer.on('startSelection', this.handleStartSelect);
      this.annotationLayer.on('select', this.handleSelect);
      this.annotationLayer.on('updateTarget', this.handleUpdateTarget);
      this.annotationLayer.on('viewportChange', this.handleViewportChange);
      this.forwardEvent('clickAnnotation', 'onClickAnnotation');
      this.forwardEvent('mouseEnterAnnotation', 'onMouseEnterAnnotation');
      this.forwardEvent('mouseLeaveAnnotation', 'onMouseLeaveAnnotation');
      document.addEventListener('keyup', this.onKeyUp);
    }
    componentWillUnmount() {
      this.annotationLayer.destroy();
      document.removeEventListener('keyup', this.onKeyUp);
    }
    get disableEditor() {
      return this.state.editorDisabled;
    }
    set disableEditor(t) {
      this.setState({ editorDisabled: t });
    }
    get disableSelect() {
      return this.annotationLayer.disableSelect;
    }
    set disableSelect(t) {
      this.annotationLayer.disableSelect = t;
    }
    get formatters() {
      return this.annotationLayer.formatters;
    }
    set formatters(t) {
      this.annotationLayer.formatters = t;
    }
    get readOnly() {
      return this.state.readOnly;
    }
    set readOnly(t) {
      this.annotationLayer.readOnly = t;
      this.setState({ readOnly: t });
    }
    get widgets() {
      return this.state.widgets;
    }
    set widgets(t) {
      this.setState({ widgets: t });
    }
    render() {
      var n;
      const t = this.state.selectedAnnotation && !this.state.editorDisabled;
      const e =
        this.state.readOnly ||
        ((n = this.state.selectedAnnotation) == null ? void 0 : n.readOnly);
      return (
        t &&
        Lh(PS, {
          ref: this._editor,
          detachable: true,
          wrapperEl: this.props.wrapperEl,
          annotation: this.state.selectedAnnotation,
          modifiedTarget: this.state.modifiedTarget,
          selectedElement: this.state.selectedDOMElement,
          readOnly: e,
          allowEmpty: this.props.config.allowEmpty,
          widgets: this.state.widgets,
          env: this.props.env,
          onAnnotationCreated: this.onCreateOrUpdateAnnotation(
            'onAnnotationCreated'
          ),
          onAnnotationUpdated: this.onCreateOrUpdateAnnotation(
            'onAnnotationUpdated'
          ),
          onAnnotationDeleted: this.onDeleteAnnotation,
          onCancel: this.onCancelAnnotation,
        })
      );
    }
  }
  var kx = '';
  var Bx = '';
  var Nx = '';
  class UE {
    constructor(t, e) {
      P(this, 'handleAnnotationCreated', (t, e) =>
        this._emitter.emit('createAnnotation', t.underlying, e)
      );
      P(this, 'handleAnnotationDeleted', (t) =>
        this._emitter.emit('deleteAnnotation', t.underlying)
      );
      P(this, 'handleAnnotationSelected', (t, e) =>
        this._emitter.emit('selectAnnotation', t.underlying, e)
      );
      P(this, 'handleAnnotationUpdated', (t, e) =>
        this._emitter.emit('updateAnnotation', t.underlying, e.underlying)
      );
      P(this, 'handleCancelSelected', (t) =>
        this._emitter.emit('cancelSelected', t.underlying)
      );
      P(this, 'handleClickAnnotation', (t, e) =>
        this._emitter.emit('clickAnnotation', t.underlying, e)
      );
      P(this, 'handleLoad', (t) => this._emitter.emit('load', t));
      P(this, 'handleSelectionCreated', (t) =>
        this._emitter.emit('createSelection', t.underlying)
      );
      P(this, 'handleSelectionStarted', (t) =>
        this._emitter.emit('startSelection', t)
      );
      P(this, 'handleSelectionTargetChanged', (t) =>
        this._emitter.emit('changeSelectionTarget', t)
      );
      P(this, 'handleMouseEnterAnnotation', (t, e) =>
        this._emitter.emit('mouseEnterAnnotation', t.underlying, e)
      );
      P(this, 'handleMouseLeaveAnnotation', (t, e) =>
        this._emitter.emit('mouseLeaveAnnotation', t.underlying, e)
      );
      P(this, '_wrap', (t) =>
        (t == null ? void 0 : t.type) === 'Annotation' ? new Dt(t) : t
      );
      P(this, 'addAnnotation', (t) =>
        this._app.current.addAnnotation(new Dt(t))
      );
      P(this, 'addDrawingTool', (t) => this._app.current.addDrawingTool(t));
      P(this, 'cancelSelected', () => this._app.current.cancelSelected());
      P(this, 'clearAnnotations', () => this.setAnnotations([]));
      P(this, 'clearAuthInfo', () => (this._env.user = null));
      P(this, 'destroy', () => L.unmountComponentAtNode(this.appContainerEl));
      P(this, 'fitBounds', (t, e) =>
        this._app.current.fitBounds(this._wrap(t), e)
      );
      P(this, 'fitBoundsWithConstraints', (t, e) =>
        this._app.current.fitBoundsWithConstraints(this._wrap(t), e)
      );
      P(this, 'getAnnotationById', (t) => {
        const e = this._app.current.getAnnotationById(t);
        if (e == null) {
          return;
        } else {
          return e.underlying;
        }
      });
      P(this, 'getAnnotations', () =>
        this._app.current.getAnnotations().map((e) => e.underlying)
      );
      P(this, 'getImageSnippetById', (t) =>
        this._app.current.getImageSnippetById(t)
      );
      P(this, 'getSelected', () => {
        const t = this._app.current.getSelected();
        if (t == null) {
          return;
        } else {
          return t.underlying;
        }
      });
      P(this, 'getSelectedImageSnippet', () =>
        this._app.current.getSelectedImageSnippet()
      );
      P(this, 'listDrawingTools', () => this._app.current.listDrawingTools());
      P(this, 'loadAnnotations', (t) =>
        fetch(t)
          .then((e) => e.json())
          .then((e) => (this.setAnnotations(e), e))
      );
      P(this, 'off', (t, e) => this._emitter.off(t, e));
      P(this, 'on', (t, e) => this._emitter.on(t, e));
      P(this, 'once', (t, e) => this._emitter.once(t, e));
      P(this, 'panTo', (t, e) => this._app.current.panTo(this._wrap(t), e));
      P(this, 'removeAnnotation', (t) =>
        this._app.current.removeAnnotation(this._wrap(t))
      );
      P(this, 'removeDrawingTool', (t) =>
        this._app.current.removeDrawingTool(t)
      );
      P(this, 'saveSelected', () => this._app.current.saveSelected());
      P(this, 'selectAnnotation', (t) => {
        const e = this._app.current.selectAnnotation(this._wrap(t));
        if (e == null) {
          return;
        } else {
          return e.underlying;
        }
      });
      P(this, 'setAnnotations', (t) => {
        const n = (t || []).map((r) => new Dt(r));
        this._app.current.setAnnotations(n);
      });
      P(this, 'setAuthInfo', (t) => (this._env.user = t));
      P(this, 'setDrawingEnabled', (t) =>
        this._app.current.setDrawingEnabled(t)
      );
      P(this, 'setDrawingTool', (t) => this._app.current.setDrawingTool(t));
      P(this, 'setServerTime', (t) => this._env.setServerTime(t));
      P(this, 'setVisible', (t) => this._app.current.setVisible(t));
      P(this, 'updateSelected', (t, e) => {
        let n = null;
        if (t.type === 'Annotation') {
          n = new Dt(t);
        } else if (t.type === 'Selection') {
          n = new Un(t.target, t.body);
        }
        if (n) {
          this._app.current.updateSelected(n, e);
        }
      });
      const n = e || {};
      this._app = L.createRef();
      this._emitter = new Qn();
      this._env = LS();
      this._element = t.element;
      if (
        window.getComputedStyle(this._element).getPropertyValue('position') ===
        'static'
      ) {
        this._element.style.position = 'relative';
      }
      kS(n.locale, n.messages);
      this.appContainerEl = document.createElement('DIV');
      this._element.appendChild(this.appContainerEl);
      L.render(
        Lh(VE, {
          ref: this._app,
          viewer: t,
          wrapperEl: this._element,
          config: n,
          env: this._env,
          onSelectionStarted: this.handleSelectionStarted,
          onSelectionCreated: this.handleSelectionCreated,
          onSelectionTargetChanged: this.handleSelectionTargetChanged,
          onAnnotationCreated: this.handleAnnotationCreated,
          onAnnotationSelected: this.handleAnnotationSelected,
          onAnnotationUpdated: this.handleAnnotationUpdated,
          onAnnotationDeleted: this.handleAnnotationDeleted,
          onCancelSelected: this.handleCancelSelected,
          onClickAnnotation: this.handleClickAnnotation,
          onLoad: this.handleLoad,
          onMouseEnterAnnotation: this.handleMouseEnterAnnotation,
          onMouseLeaveAnnotation: this.handleMouseLeaveAnnotation,
        }),
        this.appContainerEl
      );
    }
    get disableEditor() {
      return this._app.current.disableEditor;
    }
    set disableEditor(t) {
      this._app.current.disableEditor = t;
    }
    get disableSelect() {
      return this._app.current.disableSelect;
    }
    set disableSelect(t) {
      this._app.current.disableSelect = t;
    }
    get formatters() {
      return this._app.current.formatters || [];
    }
    set formatters(t) {
      if (t) {
        const e = Array.isArray(t) ? t : [t];
        this._app.current.formatters = e;
      } else {
        this._app.current.formatters = null;
      }
    }
    get readOnly() {
      return this._app.current.readOnly;
    }
    set readOnly(t) {
      this._app.current.readOnly = t;
    }
    get widgets() {
      return this._app.current.widgets;
    }
    set widgets(t) {
      this._app.current.widgets = t;
    }
  }
  var WE = (i, t) => new UE(i, t);
  return WE;
});
