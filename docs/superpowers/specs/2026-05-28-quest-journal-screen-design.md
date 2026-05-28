# Quest Journal / Job Board Screen Design

## Overview

A unified Quest Journal screen accessible anytime via the `J` hotkey. Five tabs — Story, Active Quests, Job Board, Rumours, History — provide a single place for all quest-related content. Layout follows the existing MarketScreen pattern: horizontal top tabs with a list/detail split below.

## Screen Structure

### Access & Lifecycle

- `J` key toggles the journal open/closed from any game state (on foot, cockpit, etc.)
- `ESC` also closes it
- Opening publishes `JournalOpenedEvent` — game simulation pauses, player input disabled
- Closing publishes `JournalClosedEvent` — simulation resumes, input restored
- Screen captures input focus via its own Stage

### Layout

Top-tab bar with list/detail split, matching `MarketScreen`:

```
┌──────────────────────────────────────────────────────┐
│  QUEST JOURNAL                              [X]      │
├──────┬──────┬──────┬──────┬──────┬───────────────────┤
│Story │Active│Board │Rumour│Hist. │                   │
├──────┴──────┴──────┴──────┴──────┤                   │
│                                  │                   │
│         Quest List               │   Detail Panel    │
│       (ScrollPane)               │                   │
│                                  │                   │
│                                  │                   │
└──────────────────────────────────┴───────────────────┘
```

- Root `Table` fills the screen with padding
- Tab bar: horizontal row of `TextButton`s, active tab highlighted with cyan underline/background
- Content area: `Stack` swapping between 5 tab content actors
- List/detail split: ~60/40 horizontal ratio

## Tab Specifications

### 1. Story Tab

Displays main story progression. No list/detail split — uses a custom two-column layout.

**Left column — Act progression:**
- Current act shown expanded: title, status badge ("IN PROGRESS"), description, objectives with checkmarks
- Objectives read from `SagaInstance.getActiveObjectives()`
- Future acts shown dimmed with "Complete [Act N] to unlock"
- Completed acts shown with a completion checkmark

**Right column — Context:**
- "Choices Made" section listing `SagaInstance.getChoicesMade()` with the node where each choice occurred
- "Rewards Earned" section showing cumulative credits and rep from story progress

**Empty state:** "Your story hasn't begun..." when `QuestJournal.getActiveMainStory()` is null.

**Data source:** `QuestJournal.getActiveMainStory()` (SagaInstance), `SagaRegistry` for node graph structure.

### 2. Active Quests Tab

All currently active work: jobs, faction chains, companion arcs.

**Filter bar:** Chips for All / Jobs / Faction / Companion. Filters the list below.

**Left list (ScrollPane):**
- Each row shows: type color indicator (4px left bar), quest name, short description, time remaining (if applicable), reward amount
- Type colors: Jobs = cyan, Bounty = red, Faction = purple, Companion = green
- Selected row highlighted with subtle border
- Sorted: time-limited quests first (by urgency), then alphabetical

**Right detail panel (on selection):**
- Quest title, full description
- Objectives with done/todo checkmarks
- Time remaining with countdown timer and progress bar (for timed jobs)
- Rewards breakdown (credits, rep changes)
- Quest giver name and location
- "Abandon Quest (−N Rep)" button — publishes `QuestAbandonedEvent`. Only shown for `JobInstance` quests. Saga quests (main story, faction chains, companion arcs) cannot be abandoned

**Data sources:** `QuestJournal.getActiveJobs()`, `getActiveFactionChains()`, `getActiveCompanionArcs()`.

### 3. Job Board Tab

Browse and accept new jobs. Only functional when docked at a station.

**Docked state:**
- Header shows station name and available job count
- Filter bar: All / Cargo / Bounty / Explore / Salvage / Mercenary (derived from job type enum)
- Left list: available jobs with type color, title, location, reward, difficulty stars (1-3)
- Jobs requiring faction standing the player lacks are shown dimmed with "Requires: [Faction] — [Standing Level]"
- Right detail panel: full description, difficulty, time limit, objective preview, rewards
- "ACCEPT JOB" button — publishes `JobAcceptedEvent`. Disabled with "10/10 Active" text when at max active jobs

**Undocked state:**
- Entire content area replaced with centered empty state: antenna icon, "No Station Network", "Dock at a station to browse available jobs"

**Data sources:** `JobBoard.getAvailableJobs()`, player docked state, player faction standings.

### 4. Rumours Tab

Discovered leads that haven't resolved into full quests yet. Simple scrollable list, no detail split needed.

**List rows:**
- Star icon (filled = recent, outline = old)
- Rumour title
- Source ("Overheard at bar", "News broadcast", "Data fragment", etc.)
- Age ("2 days ago", "1 week ago")
- Sorted newest first

**On click:** Selecting a rumour expands a description paragraph inline below the row (accordion style). No action buttons — rumours resolve into quests through gameplay events.

**Data source:** `QuestJournal.getRumourBoard()`.

### 5. History Tab

Completed, failed, and abandoned quest log.

**Left list (ScrollPane):**
- Each row: outcome icon (green checkmark = completed, red X = failed, yellow warning = abandoned), quest name, outcome label, timestamp
- Failed quests shown with strikethrough on the name
- Sorted newest first

**Right detail panel (on selection):**
- Quest name, type, outcome
- Rewards earned (for completed) or rep lost (for failed/abandoned)
- Completion timestamp

**Data source:** `QuestJournal.getCompletedQuests()` — new `List<CompletedQuestRecord>` field.

## Events

### New Events

| Event | Package | Fields | Published By | Purpose |
|---|---|---|---|---|
| `JournalOpenedEvent` | `ui.events` | — | `QuestJournalScreen` | Pause sim, disable player input |
| `JournalClosedEvent` | `ui.events` | — | `QuestJournalScreen` | Resume sim, restore input |
| `JobAcceptedEvent` | `mission.job` | `String jobInstanceId` | `QuestJournalScreen` | Player accepts a job from the board |
| `QuestAbandonedEvent` | `mission.shared` | `String questInstanceId` | `QuestJournalScreen` | Player abandons a quest |

### Subscribed Events

The screen subscribes to these existing events to stay updated while open:

- `ObjectiveUpdatedEvent` — refresh objective checkmarks in active/story tabs
- `QuestCompletedEvent` — move quest from active to history, refresh lists
- `QuestFailedEvent` — move quest from active to history with failure state
- `QuestDiscoveredEvent` — add new entry to rumours tab

## New Classes

| Class | Package | Extends | Purpose |
|---|---|---|---|
| `QuestJournalScreen` | `ui` | `Screen` | Main screen: Stage, tab switching, input, lifecycle |
| `StoryTabActor` | `ui.actors` | `Table` | Story tab content |
| `ActiveQuestsTabActor` | `ui.actors` | `Table` | Active quests list + detail |
| `JobBoardTabActor` | `ui.actors` | `Table` | Job board list + detail |
| `RumoursTabActor` | `ui.actors` | `Table` | Rumours list |
| `HistoryTabActor` | `ui.actors` | `Table` | History list + detail |
| `CompletedQuestRecord` | `mission.shared` | — | Data class: name, type, outcome enum, timestamp, rewards/rep |

## Modified Classes

| Class | Change |
|---|---|
| `QuestJournal` | Add `List<CompletedQuestRecord> completedQuests`, `addCompleted()`, `getCompletedQuests()` |
| `GameScreen` | Register `J` key to toggle `QuestJournalScreen` |
| `PlayerMovementSystem` | Subscribe to `JournalOpenedEvent`/`JournalClosedEvent` to disable/enable input |
| `ObjectiveTrackingSystem` | Subscribe to `QuestAbandonedEvent` — remove from active, apply rep penalty, add to history |
| `SnapshotComponentRegistry` | Register `CompletedQuestRecord` for save/load serialization |

## Visual Style

Follows the existing `UiFactory` skin and color conventions:

- **Background:** Dark navy (#16213e / #1a1a2e) matching existing screens
- **Tab active state:** Cyan (#00d4ff) text + bottom highlight bar, inactive tabs in muted gray
- **Type colors:** Cyan (cargo/general), Red (bounty/combat), Purple (faction chains), Green (companion/exploration), Gold/yellow (story, timers, credits)
- **Fonts:** Header style for tab labels and section titles, body style for descriptions, slot-name style for small labels
- **Abandon button:** Red text on dark red background, visually distinct as destructive
- **Accept button:** Cyan text on dark blue background, matching existing action button patterns

## Data Flow Summary

```
Player presses J
  → QuestJournalScreen opens
  → publishes JournalOpenedEvent
  → PlayerMovementSystem disables input
  → Screen reads QuestJournal + JobBoard for display

Player clicks "Accept Job"
  → publishes JobAcceptedEvent(jobId)
  → JobBoard/ObjectiveTrackingSystem handles acceptance
  → quest appears in Active tab

Player clicks "Abandon Quest"
  → publishes QuestAbandonedEvent(instanceId)
  → ObjectiveTrackingSystem removes from active, applies rep penalty
  → CompletedQuestRecord added to history with "abandoned" outcome

Player presses J or ESC
  → QuestJournalScreen closes
  → publishes JournalClosedEvent
  → PlayerMovementSystem re-enables input
```
