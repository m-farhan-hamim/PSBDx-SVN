# 🔒 Security Policy

## 📋 Overview

**PSBDx-SVN** takes security and transparency seriously. This document outlines our security practices, supported versions, how to report vulnerabilities, and important information about app updates and supported installation sources.

> 🧑‍💻 **Why I built this app:** I made PSBDx-SVN **just for you all** — so you don't have to face *that* problem. You know the one. I don't want to say the name of that Android SVN app available on the Google Play Store. That very popular one. The one that charges **so much money** for features that should be **free**. Yeah. *That* one. So please — read the rest of this document with that in mind. 😤

---

## 📢 Important Notice: Auto-Updater

> **⚠️ Please read this carefully before installing PSBDx-SVN.**

Unlike previous PSBDx projects, **PSBDx-SVN does NOT include an auto-update notification system** for GitHub-based installations.

### Why?

This project was developed with the **direct intention of publishing on [F-Droid](https://f-droid.org/)**. F-Droid provides its own secure, verified update mechanism, making a built-in updater redundant and potentially conflicting.

### Update Roadmap

| Version Range | Auto-Updater Status |
|:---|:---|
| **Initial Release (1.x.x)** | ❌ **Not included** |
| **All pre-release channels (ALPHA, BETA, NIGHTLY)** | ❌ **Not supported** |
| **Versions below 2.0.0** | ❌ **Not included** |
| **Versions 2.0.0 and above** | ✅ **Auto-updater will be reintroduced** |

### What This Means for You

- 🔕 **No automatic update notifications** will appear in the app for the initial release.
- 📥 You must **manually check** the [GitHub Releases](https://github.com/m-farhan-hamim/PSBDx-SVN/releases) page or F-Droid for updates.
- 🧪 **All pre-release channels** (ALPHA, BETA, NIGHTLY) will not support any form of auto-updating.
- 🚀 Once the app reaches **version 2.0.0 or higher**, a built-in updater will be reintroduced as a feature.

> 🤷 **Yes, this means you have to manually check for updates.** No, we're not sorry. Blame F-Droid's excellent update system. It's called *trusting the platform*, bestie. 💅

---

## 🏷️ Release Naming Convention

PSBDx-SVN uses a **channel-prefixed versioning scheme** so users can instantly identify the stability of any build at a glance.

| Channel | Format | Example |
|:---|:---|:---|
| **Stable** | `X.Y.Z` | `1.3.1` |
| **BETA** | `BETA-X.Y.Z` | `BETA-1.3.1` |
| **ALPHA** | `ALPHA-X.Y.Z` | `ALPHA-1.3.1` |
| **NIGHTLY** | `NIGHTLY-X.Y.Z` | `NIGHTLY-1.3.1` |

> 💡 **Tip:** If a release has **no channel prefix**, it is a **stable release** and is the recommended version for everyday use. If it has a prefix... well, good luck, soldier. 🫡

---

## ✅ Supported Installation Sources

PSBDx-SVN is **only officially supported** when installed from the following sources:

| Source | Supported | Notes |
|:---|:---:|:---|
| **GitHub Releases** | ✅ Yes | Official releases only — verify the APK matches the release page |
| **F-Droid** | ✅ Yes | Preferred distribution channel (coming soon) |
| **Google Play** | ⏳ Coming Soon | Official listing pending |
| Any other website / APK mirror / third-party store | ❌ **No** | **Unsupported — use at your own risk** |
| Sideloaded APKs from unknown sources | ❌ **No** | **Unsupported** |
| Modified, re-signed, or repackaged APKs | ❌ **No** | **Unsupported — potential security risk** |

> 🛑 **Security Warning:** If you install PSBDx-SVN from any source other than the official GitHub Releases page or F-Droid, **no support will be provided**. Such builds may have been tampered with, contain malware, or behave unexpectedly. We cannot verify their integrity.

> 🤨 **So... why do you need a modified version?** Mine is completely **free**, has **no ads**, **no trackers**, **no paywalls**, **no "Pro" tier**, **no subscription**, nothing. I'm genuinely confused. Please [open an issue](https://github.com/m-farhan-hamim/PSBDx-SVN/issues) and tell me — what are you even trying to "mod"? There's nothing to unlock. Everything is already unlocked. From day one. Forever. 🆓

> 🚫 **If you install a "PSBDx-SVN Premium Cracked Mod APK v99" from some shady website — congratulations, you played yourself.** You now have a mystery app signed by someone named "xX_Modder_Xx" with full access to your files and network. That's not a premium version, that's a free one-way ticket to becoming someone's botnet node. 💀

> 🎁 **Weird flex but okay:** Some APK mirrors claim to offer "faster downloads" or "better performance" versions of PSBDx-SVN. Bestie, it's the same code. You're just downloading it from a sketchier place with extra malware sprinkled on top. It's like buying water from a puddle instead of a tap. 💧

> 📦 **If you install from a third-party APK store, you are the beta tester.** Not for us — for the malware. And no, we can't fix your bricked phone or leaked credentials. That's a you-and-the-shady-APK-mirror problem now. 🤷

---

## 🛡️ Security Best Practices

When installing PSBDx-SVN, we recommend:

1. ✅ **Only download** the APK from [GitHub Releases](https://github.com/m-farhan-hamim/PSBDx-SVN/releases) or [F-Droid](https://f-droid.org/) (once available).
2. ✅ **Verify the source** — ensure the URL begins with `https://github.com/m-farhan-hamim/` or `https://f-droid.org/`.
3. ✅ **Check the app signature** matches the official release.
4. ✅ **Review permissions** requested by the app after installation.
5. ❌ **Do not install** APKs from Telegram channels, file-sharing sites, or unofficial APK mirrors.
6. ❌ **Do not trust** modified or "cracked" versions claiming to be PSBDx-SVN.

> 📵 **Do NOT come to our support and say:** *"I installed a modified APK from a Telegram channel and now it crashes / steals my SVN credentials / shows me ads."* We won't help. We'll just stare at you with the energy of a disappointed parent who told you not to touch the stove. 🍳

> 🕵️ **Reminder:** The only "mod" you'll ever need is the [contribution guide](./CONTRIBUTING.md). If you want a feature, open a PR. Don't download `PSBDx_SVN_ULTRA_MOD_v4_FINAL.apk` from a website that also sells you a VPN and a crypto miner. 🪙

> ⚠️ **TL;DR:** Official builds = support. Modified builds = good luck, you're on your own. We are not responsible for anything that happens after you click "Install" on a file named `PSBDx-SVN_[FULLY_UNLOCKED]_[NO_ADS]_[MOD].apk`. 🙃

---

## 📦 Supported Versions

We provide security updates for the following versions:

| Version | Supported | Notes |
|:---|:---:|:---|
| **Latest stable release** (e.g., `1.3.1`) | ✅ Yes | Always recommended |
| **Previous stable release** | ⚠️ Limited | Critical fixes only |
| **Older stable releases** | ❌ No | Please upgrade |
| **Pre-release channels** (`BETA-X.Y.Z`, `ALPHA-X.Y.Z`, `NIGHTLY-X.Y.Z`) | ⚠️ Best effort | No auto-updater, no guaranteed support |
| **Unsupported install sources** | ❌ No | See section above |

> 💡 **Always use the latest stable release** from [GitHub Releases](https://github.com/m-farhan-hamim/PSBDx-SVN/releases) or F-Droid to receive the latest security fixes. Chasing old versions like they're vintage wine won't end well. 🍷

---

## 🚨 Reporting a Vulnerability

If you discover a security vulnerability in PSBDx-SVN, **please report it responsibly**.

### How to Report

1. **Do NOT open a public GitHub issue** for security vulnerabilities.
2. Instead, use one of the following private channels:
   - 📧 **Email:** [support@psbdx.com](mailto:support@psbdx.com)
   - 🔐 **GitHub Security Advisories:** [Report a vulnerability privately](https://github.com/m-farhan-hamim/PSBDx-SVN/security/advisories/new)

### What to Include

Please provide as much detail as possible:

- A clear description of the vulnerability
- Steps to reproduce the issue
- Affected version(s) — use the exact version string (e.g., `1.3.1`, `BETA-1.3.1`, `NIGHTLY-1.3.1`)
- Channel (Stable / ALPHA / BETA / NIGHTLY)
- Potential impact
- Proof-of-concept code or screenshots (if applicable)
- Your suggested fix (optional)

### Our Response

- ⏱️ **Acknowledgment:** Within **72 hours**
- 🔍 **Initial assessment:** Within **7 days**
- 🛠️ **Fix & disclosure:** Coordinated with you before public disclosure

We deeply appreciate responsible disclosure and will credit reporters (with permission) in the release notes. 🏆

---

## 🔐 Security Features

PSBDx-SVN incorporates the following security-conscious design choices:

- 🔒 **No unnecessary permissions** — the app only requests what it needs
- 🔑 **Secure credential storage** for SVN authentication
- 🌐 **HTTPS-first** communication with SVN servers
- 📴 **No background telemetry** or analytics
- 🧾 **Fully open source** — code is publicly auditable under GPLv3
- 🚫 **No ads, no trackers, no third-party SDKs** that collect user data

> 🙏 **One more time for the people in the back:** This app exists **specifically** because paid SVN clients on the Play Store are overpriced for basic features. I made it free, ad-free, and open source *on purpose*. If you're still looking for a "cracked" version, please — [open an issue](https://github.com/m-farhan-hamim/PSBDx-SVN/issues) and explain. I am genuinely curious, and slightly concerned. 🫠

---

## 🧪 Pre-release & Test Builds

PSBDx-SVN offers **three separate pre-release channels** for testers and early adopters. Each channel serves a different purpose and has different stability guarantees.

| Channel | Version Format | Example | Purpose | Stability | Auto-Updater |
|:---|:---|:---|:---|:---:|:---:|
| **ALPHA** | `ALPHA-X.Y.Z` | `ALPHA-1.3.1` | Earliest internal builds — experimental features, possible crashes | ⚠️ Highly unstable | ❌ No |
| **BETA** | `BETA-X.Y.Z` | `BETA-1.3.1` | Feature-complete builds — public testing before stable | ⚠️ Mostly stable | ❌ No |
| **NIGHTLY** | `NIGHTLY-X.Y.Z` | `NIGHTLY-1.3.1` | Automated daily builds from the latest source | 🔥 Bleeding edge, may break | ❌ No |

### ⚠️ Important Notes for All Pre-release Channels

- ❌ **No auto-updater support** — see [Auto-Updater section](#-important-notice-auto-updater)
- ❌ **No guaranteed backward compatibility** between builds
- ❌ **No support** if installed from unofficial sources
- ⚠️ May contain bugs, incomplete features, or data-loss risks
- 🔁 **Manual updates only** — check the GitHub Releases page regularly
- ✅ Feedback is welcome via [GitHub Issues](https://github.com/m-farhan-hamim/PSBDx-SVN/issues)
- 📧 For private feedback, contact [support@psbdx.com](mailto:support@psbdx.com)

> 🧠 **Recommendation:** Use the **stable release** (e.g., `1.3.1` — no prefix) unless you are actively testing. NIGHTLY builds are not recommended for daily use. Unless you enjoy chaos. Then by all means. 🔥

---

## 📜 License

This project is licensed under the **GNU General Public License v3.0 (GPL-3.0)**.

See the [LICENSE](https://github.com/m-farhan-hamim/PSBDx-SVN/blob/main/LICENSE) file for details.

---

## 📬 Contact

- 🐛 **Bug reports & feature requests:** [GitHub Issues](https://github.com/m-farhan-hamim/PSBDx-SVN/issues)
- 🔐 **Security disclosures:** GitHub Security Advisories *(preferred)* or [support@psbdx.com](mailto:support@psbdx.com)
- 📧 **General support:** [support@psbdx.com](mailto:support@psbdx.com)
- 💬 **General discussion:** [GitHub Discussions](https://github.com/m-farhan-hamim/PSBDx-SVN/discussions)

---

<div align="center">

**Thank you for helping keep PSBDx-SVN and its users safe! 🛡️**

Made with ❤️ by [M. Farhan Hamim](https://github.com/m-farhan-hamim)

</div>
