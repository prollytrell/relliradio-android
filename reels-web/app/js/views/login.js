import * as api from "../api.js";
import { h, button } from "../ui.js";

export function loginView(onDone) {
  const s = api.getSession() || {};
  const server = h("input", { type: "url", value: s.server || defaultServer(), autocomplete: "url", "aria-label": "Server address", required: true });
  const user = h("input", { type: "text", value: s.userName || "", autocomplete: "username", "aria-label": "Username" });
  const pass = h("input", { type: "password", autocomplete: "current-password", "aria-label": "Password" });
  const error = h("p.login-error", { role: "alert" });
  const codeBox = h("div.code-box", { hidden: true });
  const fields = h("div.login-fields",
    h("label", h("span", "Server address"), server),
    h("label", h("span", "Username"), user),
    h("label", h("span", "Password"), pass));
  let polling = null;

  const submit = async (e) => {
    e?.preventDefault();
    error.textContent = "";
    const base = api.normalizeServer(server.value);
    try {
      await api.finishSignIn(base, await api.login(base, user.value.trim(), pass.value));
      onDone();
    } catch (err) { error.textContent = err.message; }
  };

  // Quick Connect: show a code; approve it from a device that's already signed in to Jellyfin.
  const quick = async () => {
    error.textContent = "";
    const base = api.normalizeServer(server.value);
    try {
      const qc = await api.quickConnectStart(base);
      fields.hidden = true; buttons.hidden = true; codeBox.hidden = false;
      codeBox.replaceChildren(h("span.hint", "Your code"), h("strong.code", qc.Code),
        h("p", "On a device that's already signed in to Jellyfin, open your profile → Quick Connect and enter this code."),
        button("Cancel", null, cancel));
      let tries = 0;
      polling = setInterval(async () => {
        if (++tries > 300) { cancel(); error.textContent = "The code expired. Try again."; return; }
        try {
          const st = await api.quickConnectState(base, qc.Secret);
          if (st.Authenticated) { clearInterval(polling); await api.finishSignIn(base, await api.quickConnectFinish(base, qc.Secret)); onDone(); }
        } catch {}
      }, 2000);
    } catch (err) { error.textContent = err.message; }
  };
  const cancel = () => { clearInterval(polling); fields.hidden = false; buttons.hidden = false; codeBox.hidden = true; };

  const buttons = h("div.login-buttons",
    h("button.pill.primary.wide", { type: "submit" }, "Sign in"),
    button("Sign in with a code (Quick Connect)", null, quick));

  return h("div.login",
    h("form.login-card", { onsubmit: submit },
      h("img.login-logo", { src: "brand/logo.svg", alt: "" }),
      h("h1.wordmark", "RelliReels"),
      h("p.hint", "Sign in with your Jellyfin account."),
      fields, codeBox, buttons, error,
      h("p.hint.small", api.DESKTOP
        ? "Jellyfin usually runs on port 8096. Away from home? Turn on WireGuard first."
        : "Jellyfin usually runs on port 8096. Away from home? Turn on WireGuard first; the same address works through the VPN.")));
}

function defaultServer() {
  // Served next to Jellyfin through Caddy (/jellyfin)? Use that; otherwise the home server.
  if (!api.DESKTOP && location.protocol.startsWith("http") && document.querySelector('meta[name="jellyfin-path"]')) {
    return location.origin + document.querySelector('meta[name="jellyfin-path"]').content;
  }
  return "http://192.168.1.10:8096";
}
