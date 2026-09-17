import { n as e } from "./chunk-DiqZc92J.js";
import { $ as t, C as n, L as r, R as i, T as a, at as o, et as s, l as c, lt as l, n as u, o as d, r as f, s as ee, t as p, u as m, y as h } from "./icons-CwakCZgK.js";
import { l as te, o as g } from "./consts-DOP4bWMB.js";
import { a as _, i as v, n as y, r as b } from "./copilot-ui-state-Dc6l_5DA.js";
import { t as ne } from "./stats-CRkPKCLQ.js";
import { i as re, n as ie, r as x, t as S } from "./directive-DWLihZIi.js";
import { n as C, t as w } from "./copilot-stored-machine-state-D6qB_Peh.js";
import { n as T } from "./copilot-notification-CCNJdNg4.js";
import { n as E } from "./early-project-state-LGwavSyI.js";
//#region node_modules/lit-html/directives/unsafe-html.js
var D, O, k = e((() => {
	c(), x(), D = class extends ie {
		constructor(e) {
			if (super(e), this.it = d, e.type !== re.CHILD) throw Error(this.constructor.directiveName + "() can only be used in child bindings");
		}
		render(e) {
			if (e === d || e == null) return this._t = void 0, this.it = e;
			if (e === ee) return e;
			if (typeof e != "string") throw Error(this.constructor.directiveName + "() called with a non-string value");
			if (e === this.it) return this._t;
			this.it = e;
			let t = [e];
			return t.raw = t, this._t = {
				_$litType$: this.constructor.resultType,
				strings: t,
				values: []
			};
		}
	}, D.directiveName = "unsafeHTML", D.resultType = 1, O = S(D);
})), A = e((() => {
	k();
}));
//#endregion
//#region frontend/copilot/shared/copilot-userinfo-util.ts
function ae() {
	let e = y.userInfo;
	return !e || e.copilotProjectCannotLeaveLocalhost ? !1 : C.isSendErrorReportsAllowed();
}
var oe = e((() => {
	ne(), b(), $(), w(), n(), T(), s();
}));
//#endregion
//#region frontend/copilot/shared/hotswap-utils.ts
function j() {
	return y.idePluginState?.supportedActions?.find((e) => e === "restartApplication");
}
function M() {
	i(`${g}plugin-restart-application`, {}, () => {}).catch((e) => {
		I("Error restarting server", e);
	});
}
var N = e((() => {
	r(), te(), $(), _(), T(), s(), b(), E();
}));
//#endregion
//#region frontend/copilot/shared/copilot-error-handler.ts
function P(e) {
	if (e === void 0) return !1;
	let t = Object.keys(e);
	return t.length === 1 && t.includes("message") || t.length >= 3 && t.includes("message") && t.includes("exceptionMessage") && t.includes("exceptionStacktrace");
}
function se() {
	let e = "A server restart is required";
	return j() ? h(m`${e}${F()}`) : h(m`${e}`);
}
function F() {
	return j() ? m`<vaadin-button
      class="mt-2"
      theme="primary"
      @click=${(e) => {
		let t = e.target;
		t.disabled = !0, t.innerText = "Restarting...", M();
	}}>
      Restart Now
    </vaadin-button>` : d;
}
function I(e, n) {
	let r = n;
	if (r && Y(r)) {
		X(r);
		return;
	}
	let i = P(n) ? n.exceptionMessage ?? n.message : n, o = {
		type: t.ERROR,
		message: "Copilot internal error",
		details: e + (i ? `\n${i}` : "")
	};
	P(n) && n.suggestRestart && j() && (o.details = h(m`${e}<br />${i} ${F()}`), o.delay = 3e4), a(o);
	let s;
	s = n instanceof Error ? n.stack : P(n) ? n?.exceptionStacktrace?.join("\n") : n?.toString(), v.emit("system-info-with-callback", {
		callback: (t) => v.send("copilot-error", {
			message: `Copilot internal error: ${e}`,
			details: s,
			versions: t
		}),
		notify: !1
	});
}
function L(e) {
	return e?.stack?.includes("cdn.vaadin.com/copilot") || e?.stack?.includes("/copilot/copilot/") || e?.stack?.includes("/copilot/copilot-private/");
}
function R() {
	let e = window.onerror;
	window.onerror = (t, n, r, i, a) => {
		if (L(a)) {
			I(t.toString(), a);
			return;
		}
		e && e(t, n, r, i, a);
	}, l((e) => {
		L(e) && I("", e);
	});
	let t = window.Vaadin.ConsoleErrors;
	if (Array.isArray(t)) for (let e of t) Array.isArray(e) ? Q.push(...e) : Q.push(e);
	B((e) => Q.push(e));
}
function z(e, t, n, r, i, a) {
	let o = { ...e }, s = window.Vaadin.copilot.tree, c = window.Vaadin.copilot.customComponentHandler;
	o.nodes.forEach((e) => {
		e.node = s.allNodesFlat.find((t) => {
			if (!t.isFlowComponent) return !1;
			let n = t.node;
			return n.uiId === e.uiId && n.nodeId === e.nodeId;
		});
	});
	let l = [];
	n && l.push(`Error Message -> ${n}`), r && l.push(`Error Details -> ${r}`), l.push(`Active Level -> ${c.getActiveDrillDownContext() ? c.getActiveDrillDownContext()?.nameAndIdentifier : "No active level"}`), o.nodes.length > 0 && (l.push("\nRelevant Nodes:"), o.nodes.forEach((e) => {
		l.push(`${e.relevance} -> ${e.node?.nameAndIdentifier ?? "Node not found"}`);
	})), o.relevantPairs.length > 0 && (l.push("\nAdditional Info:"), o.relevantPairs.forEach((e) => {
		l.push(`${e.relevance} -> ${e.value}`);
	})), a && (l.push("Versions"), l.push(a));
	let u = {
		name: "Info",
		content: l.join("\n")
	};
	o.items.unshift(u), i && o.items.push({
		name: "Stacktrace",
		content: i
	}), v.emit("system-info-with-callback", {
		callback: (e) => {
			o.items.push({
				name: "Versions",
				content: e
			}), t(o);
		},
		notify: !1
	});
}
function B(e) {
	let n = window.Vaadin.ConsoleErrors;
	window.Vaadin.ConsoleErrors = { push: (r) => {
		r[0] === null || r[0] === void 0 || (r[0].type !== void 0 && r[0].message !== void 0 ? e({
			type: r[0].type,
			message: r[0].message,
			internal: !!r[0].internal,
			details: r[0].details,
			link: r[0].link
		}) : e({
			type: t.ERROR,
			message: r.map((e) => V(e)).join(" "),
			internal: !1
		}), n.push(r));
	} };
}
function V(e) {
	return e.message ? e.message.toString() : e.toString();
}
var H, U, W, G, K, q, J, Y, X, Z, Q, $ = e((() => {
	f(), A(), o(), _(), b(), oe(), N(), n(), s(), u(), H = (e, t) => e.error ? (Z(e.error, t), !0) : !1, U = (e, n, r) => {
		a({
			type: t.ERROR,
			message: e,
			details: h(m`${W(n)} ${K(r)}`),
			delay: 3e4
		});
	}, W = (e) => e.length === 0 ? d : e.length < 80 ? G(e) : m`<vaadin-details class="flex flex-col peer w-full" theme="no-padding reverse">
    <vaadin-details-summary class="font-medium -ms-3 self-start text-secondary text-xs" slot="summary"
      >Details</vaadin-details-summary
    >
    ${G(e)}
  </vaadin-details>`, G = (e) => m`<code class="codeblock"
    >${O(e)}<copilot-copy class="absolute end-0 flex top-0"></copilot-copy
  ></code>`, K = (e) => e ? m`
    <vaadin-button
      class="peer-has-[[opened]]:mt-2"
      @click="${() => {
		e && v.emit("submit-exception-report-clicked", e);
	}}"
      id="report-issue">
      <vaadin-icon slot="prefix" .svg="${p.bugReport}"></vaadin-icon>
      Report Issue</vaadin-button
    >
  ` : d, q = (e, t, n, r, i) => {
		let a = y.newVaadinVersionState?.versions?.length === 0;
		i && a ? z(i, (n) => {
			U(e, t, n);
		}, e, t, n) : U(e, t), ae() && (r?.templateData && typeof r.templateData == "string" && r.templateData.startsWith("data") && (r.templateData = "<IMAGE_DATA>"), v.emit("system-info-with-callback", {
			callback: (t) => v.send("copilot-error", {
				message: e,
				details: String(n).replace("	", "\n") + (r ? `\n \nRequest: \n${JSON.stringify(r)}\n` : ""),
				versions: t
			}),
			notify: !1
		})), y.clearOperationWaitsHmrUpdate();
	}, J = ["unsupported-source-language", "mixed-language-not-supported"], Y = (e) => !!e?.code && J.includes(e.code), X = (e) => {
		a({
			type: t.WARNING,
			message: e.exceptionMessage?.trim() || e.message
		}), y.clearOperationWaitsHmrUpdate();
	}, Z = (e, t) => {
		if (Y(e)) {
			X(e);
			return;
		}
		q(e.message, e.exceptionMessage ?? "", e.exceptionStacktrace?.join("\n") ?? "", t, e.exceptionReport);
	}, Q = [];
}));
//#endregion
export { H as a, N as c, A as d, O as f, I as i, M as l, Q as n, $ as o, se as r, R as s, B as t, j as u };
