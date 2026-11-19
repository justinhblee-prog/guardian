const GUARDIAN_AD_LABELS = new Set([
  "advertisement",
  "advertisement space",
  "sponsored advertisement",
  "your ad here"
]);

function normalizeGuardianAdLabel(value) {
  return String(value || "").replace(/\s+/g, " ").trim().toLowerCase();
}

function shouldHideGuardianAdLabel(value, rect) {
  return GUARDIAN_AD_LABELS.has(normalizeGuardianAdLabel(value)) &&
    Number(rect?.width) >= 200 &&
    Number(rect?.height) >= 60 &&
    Number(rect?.height) <= 500;
}

function installGuardianCosmeticFiltering() {
  if (!document.documentElement) return;
  let scanQueued = false;

  const scan = () => {
    if (!document.body) return;
    for (const node of document.querySelectorAll("body *")) {
      if (!(node instanceof HTMLElement)) continue;
      if (node.dataset.guardianCosmeticHidden === "1") continue;
      if (!GUARDIAN_AD_LABELS.has(normalizeGuardianAdLabel(node.textContent))) continue;

      let candidate = node;
      for (let depth = 0; depth < 4 && candidate; depth++, candidate = candidate.parentElement) {
        const label = normalizeGuardianAdLabel(candidate.textContent);
        if (!GUARDIAN_AD_LABELS.has(label)) continue;
        if (!shouldHideGuardianAdLabel(label, candidate.getBoundingClientRect())) continue;

        candidate.dataset.guardianCosmeticHidden = "1";
        candidate.style.setProperty("display", "none", "important");
        break;
      }
    }
  };

  scan();
  const observer = new MutationObserver(() => {
    if (scanQueued) return;
    scanQueued = true;
    requestAnimationFrame(() => {
      scanQueued = false;
      scan();
    });
  });
  observer.observe(document.documentElement, {
    childList: true,
    subtree: true,
    characterData: true
  });
}

if (typeof document !== "undefined") {
  if (document.documentElement) {
    installGuardianCosmeticFiltering();
  } else {
    document.addEventListener("DOMContentLoaded", installGuardianCosmeticFiltering, { once: true });
  }
}

if (typeof module !== "undefined") {
  module.exports = { normalizeGuardianAdLabel, shouldHideGuardianAdLabel };
}
