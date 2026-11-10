# Phase 1 — Research: TikTok API / Data Export limits

Checked on 2026-11-10 against TikTok for Developers documentation and public parsers of the export file.

## 1. Controlling the For You feed
- There is **no public TikTok API that lets a third-party app change, re-weight or filter a user's For You feed**.
- Therefore this app **does not claim to control the TikTok algorithm**. What it does instead (all real and legitimate):
  1. Turns a prompt into categories, keywords, weights and ranking rules.
  2. Builds a *search session plan* (e.g. 20 slots, Roblox ×10 / AI Tools ×6 / Programming ×4), each slot opens a normal TikTok search link.
  3. Scores and ranks clips that the user shares into the app (Android share sheet) or pastes (caption / hashtags).
  4. Reminds the user to use TikTok's own "Not interested" on unwanted topics — the only signal TikTok itself accepts.

## 2. Getting the user's Like history
| Route | Usable here? | Why |
|---|---|---|
| **Download your data** (user requests a ZIP in the TikTok app: Settings › Account › Download your data) | ✅ Used | The user's own file. Contains *Like List* with a date and a video link for each liked video. |
| **Data Portability API** | ❌ Not for this app | Requires an approved Data Portability application **and** a separate Login Kit review before any data request; applicant must be an organisation with a live website and matching-domain email. Liked videos are in its Full Archive ("Likes and Favourites" → Like List date + video link). |
| Display API / Login Kit (`user.info`, `video.list`) | ❌ | Only returns the user's own profile and own posted videos, not likes. |
| Research API | ❌ | Restricted to approved academic/non-profit researchers. |
| Scraping / private endpoints / session cookies | ❌ Forbidden by the brief | Would require the user's credentials and break TikTok's terms. |

## 3. Export file format (what the importer accepts)
The layout has changed over time, so the importer walks the whole JSON tree instead of one fixed path:
- Classic: `Activity › Like List › ItemFavoriteList[] { Date, Link }`
- Newer: `Likes and Favorites › Like List › ItemFavoriteList[] { date, link }` (lower-case keys, extra wrapper sections such as `Your Activity`)
- TXT export: `Activity/Like List.txt` with `Date: …` / `Link: …` line pairs
- The ZIP as downloaded (JSON or TXT inside)

Only arrays under a **Like List** key are read; *Favorite Videos*, *Watch History*, messages, etc. are ignored.

Timestamps look like `2024-11-05 21:14:03` without a zone. The app treats them as **UTC by default** and converts to the phone's time zone for display; Settings has a switch to read them as device local time instead if a user's file turns out to be local. Exact-offset ISO strings and epoch seconds/millis are also accepted.

Each like only carries **date + link** — no caption, creator name or hashtags. That is why Like History shows Video ID / link, and why the AI Feed ranker works on text the user shares or pastes.

## Sources
- TikTok for Developers — Data Portability data types: https://developers.tiktok.com/docs/en/data-portability-data-types
- TikTok for Developers — Data Portability application guidelines: https://developers.tiktok.com/docs/en/data-portability-api-application-guidelines
- Export structure examples: https://github.com/zollo/tiktok-downloader/blob/main/data_structure.yml , https://github.com/rorosaga/cicada/pull/474 , https://levon003.github.io/2025/01/16/tiktok-download.html
