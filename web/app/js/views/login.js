import * as api from "../api.js";
import { h, logo } from "../ui.js";

// Sign-in screen. The server field stays hidden when RelliRadio is served by the
// same Caddy site that forwards /rest to Navidrome, which is the normal setup.
export function loginView(onDone) {
  const error = h("p.form-error", { role: "alert" });
  const server = h("input.field", { type: "url", name: "server", placeholder: "https://radio.example.duckdns.org", autocomplete: "url" });
  const user = h("input.field", { type: "text", name: "username", autocomplete: "username", autocapitalize: "none", spellcheck: "false", required: true });
  const pass = h("input.field", { type: "password", name: "password", autocomplete: "current-password", required: true });
  const submit = h("button.btn.primary.wide", { type: "submit" }, "Sign in");
  const desktop = !!window.relliDesktop;
  if (desktop) server.value = localStorage.getItem("relli.lastServer") || "http://192.168.1.10:4533";
  const serverRow = h("label.field-label.server-row", { hidden: !desktop }, "Server address", server,
    h("span.hint", "Only needed when RelliRadio and Navidrome live at different addresses."));
  const toggle = h("button.link.small", { type: "button", hidden: desktop, onclick: () => { serverRow.hidden = !serverRow.hidden; toggle.hidden = true; server.focus(); } }, "Use a different server");

  const form = h("form.login-form", {
    onsubmit: async (e) => {
      e.preventDefault();
      error.textContent = "";
      submit.disabled = true;
      submit.textContent = "Signing in…";
      try {
        await api.login(serverRow.hidden ? "" : server.value, user.value, pass.value);
        if (desktop) localStorage.setItem("relli.lastServer", server.value);
        onDone();
      } catch (err) {
        error.textContent = err.message;
        submit.disabled = false;
        submit.textContent = "Sign in";
      }
    },
  },
    h("label.field-label", "Username", user),
    h("label.field-label", "Password", pass),
    serverRow,
    error,
    submit,
    toggle
  );

  return h("div.login",
    h("div.login-panel",
      logo("login-logo"),
      h("h1.wordmark", "RelliRadio"),
      h("p.login-sub", "Sign in with your Navidrome account."),
      form
    )
  );
}
