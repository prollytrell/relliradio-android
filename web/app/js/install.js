// "Install app" support. Chrome, Edge, Brave and Android fire beforeinstallprompt
// when the page qualifies (HTTPS + manifest + service worker).
// iPhone has no prompt: Safari > Share > Add to Home Screen instead.

let deferred = null;

window.addEventListener("beforeinstallprompt", (e) => {
  e.preventDefault();
  deferred = e;
});

window.addEventListener("appinstalled", () => { deferred = null; });

export function canInstall() {
  return !!deferred;
}

export async function promptInstall() {
  if (!deferred) return;
  deferred.prompt();
  await deferred.userChoice;
  deferred = null;
}
