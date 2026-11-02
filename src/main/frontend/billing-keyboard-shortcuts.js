const ENTER = event => event.key === 'Enter' || event.code === 'NumpadEnter';
const tag = node => node?.tagName?.toUpperCase();
const editableTags = new Set(['INPUT', 'TEXTAREA', 'SELECT', 'VAADIN-TEXT-AREA',
  'VAADIN-TEXT-FIELD', 'VAADIN-NUMBER-FIELD', 'VAADIN-INTEGER-FIELD', 'VAADIN-BIG-DECIMAL-FIELD']);

export function installBillingKeyboardShortcuts(host, {windowMillis, document: doc = document,
  now = () => performance.now(), schedule = setTimeout, cancel = clearTimeout,
  observe = callback => new MutationObserver(callback)} = {}) {
  // One active POS listener, including when a fresh route replaces a detached host.
  doc.__ccBillingKeyboard?.dispose();
  const keyboardRoot = doc.defaultView || doc;
  let first = null, timer = null, pending = false, disposed = false, held = false, suppressRelease = false;
  const modal = () => !!doc.querySelector('vaadin-dialog-overlay[opened],vaadin-confirm-dialog-overlay[opened],[role="dialog"][aria-modal="true"]');
  const overlay = () => !!doc.querySelector('vaadin-combo-box-overlay[opened],vaadin-select-overlay[opened],vaadin-context-menu-overlay[opened]');
  const reset = ({release = true} = {}) => {
    if (timer !== null) cancel(timer);
    first = null; timer = null;
    if (release) pending = false;
  };
  const consume = event => { event.preventDefault(); event.stopImmediatePropagation(); };
  const pathOf = event => event.composedPath?.() || [event.target];
  const context = path => {
    const combo = path.find(node => tag(node) === 'VAADIN-COMBO-BOX');
    if (combo) {
      // An idle, empty product search is the established POS resting context.
      if (!combo.classList?.contains('cc-pos-product-search') || combo.opened || String(combo.filter || '').trim()) return null;
      return {button: null};
    }
    if (path.some(node => node?.isContentEditable || (editableTags.has(tag(node)) && !node.readOnly && !node.disabled)
        || (node?.getAttribute?.('role') === 'textbox' && !node.readOnly && node.getAttribute('aria-readonly') !== 'true')
        || ['combobox','listbox','menu','menuitem','slider'].includes(node?.getAttribute?.('role')))) return null;
    return {button: [...path].reverse().find(node => ['BUTTON','VAADIN-BUTTON','A'].includes(tag(node))
      || ['button','link'].includes(node?.getAttribute?.('role'))) || null};
  };
  const down = event => {
    if (disposed || !host.isConnected) { reset(); return; }
    if (!ENTER(event)) { reset({release: false}); return; }
    if (event.isComposing || event.altKey || event.ctrlKey || event.metaKey || event.shiftKey) { reset({release: false}); return; }
    if (event.repeat || held) { if (suppressRelease) consume(event); return; }
    held = true;
    if (modal()) { reset({release: false}); return; }
    if (pending) { consume(event); suppressRelease = true; return; }
    const path = pathOf(event);
    // Ignore events outside this route, except its empty resting search context.
    if (!path.includes(host) && path[0] !== doc.body && path[0] !== doc.documentElement) { reset({release: false}); return; }
    const eligible = !overlay() && context(path);
    if (!eligible) { reset({release: false}); return; }
    const time = now();
    consume(event); suppressRelease = true;
    if (first !== null && time >= first && time - first <= windowMillis) {
      reset({release: false}); pending = true;
      host.dispatchEvent(new CustomEvent('cc-pos-open-payment'));
      return;
    }
    reset({release: false}); first = time;
    // A single Enter on a button still activates it. Defer that native action until
    // the double-Enter window expires so it can never also run alongside Cobrar.
    const button = eligible.button;
    timer = schedule(() => {
      first = null; timer = null;
      if (button?.isConnected && !disposed && !modal() && !overlay()
          && !button.disabled && doc.activeElement && (doc.activeElement === button || button.contains?.(doc.activeElement))) button.click();
    }, windowMillis + 1);
  };
  const up = event => {
    if (!ENTER(event)) return;
    held = false;
    if (suppressRelease) consume(event);
    suppressRelease = false;
  };
  const interaction = () => reset({release: false});
  const blur = () => { held = false; suppressRelease = false; reset({release: false}); };
  const observer = observe(() => { if (!host.isConnected) api.dispose(); else if (modal() || overlay()) interaction(); });
  const api = {reset, dispose() {
    if (disposed) return;
    disposed = true; reset(); observer.disconnect();
    keyboardRoot.removeEventListener('keydown', down, true); keyboardRoot.removeEventListener('keyup', up, true);
    for (const name of ['pointerdown','focusin','input','change','wheel']) doc.removeEventListener(name, interaction, true);
    doc.defaultView?.removeEventListener('blur', blur);
    if (doc.__ccBillingKeyboard === api) delete doc.__ccBillingKeyboard;
    if (host.__ccBillingKeyboard === api) delete host.__ccBillingKeyboard;
  }};
  keyboardRoot.addEventListener('keydown', down, true); keyboardRoot.addEventListener('keyup', up, true);
  for (const name of ['pointerdown','focusin','input','change','wheel']) doc.addEventListener(name, interaction, true);
  doc.defaultView?.addEventListener('blur', blur);
  observer.observe(doc.documentElement, {subtree: true, childList: true, attributes: true, attributeFilter: ['opened','aria-modal']});
  doc.__ccBillingKeyboard = api; host.__ccBillingKeyboard = api;
  return api;
}

if (typeof window !== 'undefined') window.ContaCloudBillingKeyboard = {install: installBillingKeyboardShortcuts};
