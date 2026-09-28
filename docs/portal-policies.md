# Portal and starting policies — 1.1.0

## Grounded instant placement and arrival contact

The former summon search sampled only nine cells (forward distances 2/3/4, lateral offsets 0/±2) at the player's current block height and accepted unsupported air. This excluded short corridors, odd lateral offsets and terrain steps. `Portals.nearby` now uses a precomputed, deterministically ranked forward half-disc of radius four and vertical offsets ±2. The familiar position two blocks ahead remains preferred. Every candidate requires nonhazardous sturdy support, free 1×2 body space, no contact with the player's current body, and line of sight; replaceable vegetation/snow and reclaimable portal remnants retain their existing handling. No neighboring clearance or terrain modification is required. This is an automatic summon policy, not an added restriction on permanent placement or existing portal traversal. Inside failure still uses the established escape path.

A return search can legitimately choose the doorway center when it is the only safe footing. The former 15-tick cooldown then expired while the player's body still touched the trigger, causing another teleport. Both transfer paths now snapshot registered doorway surfaces intersecting the arrived player's body. These contacts remain suppressed until the body leaves them; elapsed time alone never releases them. Server ticks and contact processing release departed surfaces, and logout/server shutdown clear runtime state. Existing cooldown and traversal budgets remain independent. This covers safe nearby fallback positions as well as the nominal endpoint and requires no saved-data changes.

## Leashed companions

Actual portal traversal captures the travelling player's leash graph before changing dimension. `LeashedTravel` scans loaded source entities once, indexes `Leashable` holders and follows connected vehicle/passenger trees without source chunk loads or pet-ownership inference. Both entry paths and physical doorway exits invoke the same helper. Login, respawn, commands and direct F recovery do not trigger companion transport.

The player completes safe travel first. Each companion group searches within six horizontal blocks and two vertical blocks of arrival, reserving collision-free dry space for the root and its entire seated passenger tree, away from the player and already transferred groups. It requires nonhazardous solid footing and respects world borders/build limits. Extreme modded bounds above 16 blocks horizontally or 24 vertically are rejected before collision scans. Terrain is never changed for companions. Missing space cannot prevent the player's escape.

Minecraft 1.21.1's `Entity.changeDimension(DimensionTransition)` recursively moves passengers and preserves UUIDs/entity NBT. Its NeoForge travel and mounting hooks remain authoritative; `canChangeDimensions` is checked for every member first. A hook may refuse an individual passenger while allowing the vehicle, following the native behavior; refusals are reported without deleting or recreating the rejected entity. An unexpected runtime transfer failure is logged with entity/type/dimensions and also reported. Arbitrary broken mod implementations are not transactionally recoverable.

Use the transition callback/returned instances to restore the captured leash edges. Destination `ServerLevel.getEntity(UUID)` can still be empty until the chunk becomes tracked, so it is not proof of transfer failure. Successful arrivals receive vanilla portal tickets and zero velocity/fall distance; seated passengers are positioned immediately. Broken cross-dimension edges drop one lead rather than retaining a stale holder. Standard `Leashable` implementations (including vanilla 1.21.1 boats) work without a mod-specific allowlist. Unconnected pets are untouched, with no promise that vanilla or another mod will teleport them across dimensions.

No persistent state, configuration options or protocol changes are introduced. The refusal message is maintained in all eight locale catalogs.

## Configuration

The server's `config/elsebase-common.toml` controls these options. All three appear in the native NeoForge Config screen with localized labels/help. No world regeneration or data migration is required.

| Key | Default | Behavior |
| --- | --- | --- |
| `portals.combatLockSeconds` | `3` | Whole seconds after an incoming attack before another instant summon/recall; 0 disables, maximum 300. |
| `world.startInBackdoor` | `false` | New Elsebase players start at their personal anchor on their first login and receive a respawn point there. |
| `portals.requireKnownReturn` | `false` | Inside F requests require an existing instant pair or a remembered outside destination. |

## Attack delay

NeoForge 21.1.250's `LivingIncomingDamageEvent` fires before shield/armor mitigation. A positive, non-cancelled incoming event whose damage source identifies another living attacker starts the delay. Melee attacks, owned arrows and other attacker-attributed hits count even when armor or a shield absorbs the damage. Environmental damage, self-inflicted hits, ongoing burning, ordinary poison/magic ticks, wither ticks and lingering effect clouds do not renew it. Third-party attacks must report their attacker through Minecraft's damage-source API to be recognized.

The timestamp lives in the player's normal persistent entity data and uses Overworld game ticks. Logout/relogin does not clear it, and game-time changes to day/night do not affect it. The delay pauses while the server is stopped; death creates a fresh combat state. At low TPS, three game seconds can take longer than three wall-clock seconds.

`Portals.summon` checks the policy when executing a queued request, covering both F and `/elsebase portal`, including direct escape via F. Existing portal crossing and permanent portal tools are unchanged. Repeated rejected requests retain the normal request rate limit and do not move or delete an existing pair.

## First join and respawn

The login handler runs after vanilla adds the player to the world, then prepares/repairs the personal anchor and teleports there. It does not create a portal pair or remember the transient vanilla starting position as an outside destination. A brief initial vanilla dimension transition is possible.

Existing Elsebase home reservations identify returning players, including 1.0.0 players without the new flags. Enabling this option on an existing world does not move them. Completed starts are also recorded in vanilla's `PlayerPersisted` compound so normal saves and death cloning retain the decision. Disabling and re-enabling the option does not repeat completed starts.

The initial forced respawn point is the personal anchor. Personal-anchor respawns repair missing support. If a starter's chosen respawn point is missing, the native respawn-position event redirects to their current anchor. Valid other respawn choices, End-return transitions and already-overridden respawn transitions are respected. This recovery is active only while `startInBackdoor` is enabled. Moving the portal anchor does not change an otherwise valid, separately stored vanilla respawn point.

Initial starts and respawns share the obstruction recovery below. If protection or an exceptional obstruction still prevents a safe landing, the player is disconnected with a translated explanation. Persistent pending state retries on the next login after an operator resolves the obstruction or permission denial.

## Blocked personal arrivals

`AnchorArrival.resolve` is the shared entry/start/respawn contract. Passive `Anchors.ensure` remains a best-effort, non-destructive preparation step and is no longer an outside-summon veto. Creating an outside doorway does not itself trigger destructive recovery.

1. Repair ordinary missing support/marker where permitted and test the exact anchor with the player's **standing** dimensions, including when the player is crouched or respawning.
2. Search horizontal square rings up to eight blocks away, first at the anchor elevation, then at offsets +1, -1, +2, -2. Require solid nonhazardous footing, dry collision-free headroom and valid world bounds. Search is bounded to 1,445 candidates and at most nine horizontal chunks. No heightmap or world-spawn fallback runs for personal entry.
3. Only if no safe landing exists, repair the saved anchor column: marker, one headroom block and unsafe support (three changed blocks maximum). Recheck safety before teleporting. The authoritative saved anchor and outside return remain unchanged.

Emergency repair can delete a blocking machine/container **including its contents**, without drops. It sends a translated player message and logs every changed position/state. It does not clear the surrounding room, move the anchor, or remove foreign registered portal blocks or another owner's saved marker/support. Normal protection events still apply: a claim denial may require operator intervention. World border, missing dimensions, exceptional modded collision shapes and occupying entities can also prevent a valid landing; the resolver returns failure instead of claiming entry succeeded.

`WorldEdits.recoverAnchor` validates the owner-derived three-block footprint and allowed replacement states before using the transaction engine. Ordinary structural edits retain their block-entity prohibition. Emergency staging captures full snapshots before detaching inventories; cancellation restores states and contents without duplicating drops. Initial/dimension-change preparation does not perform this destruction; permanent pairs and outside return recovery keep their previous contracts.

## Known returns

With `requireKnownReturn` enabled, a player inside without a recorded outside destination receives an explanatory message. No world-spawn escape, portal replacement or cooldown mutation occurs for that request. An outside summon works normally and records its return. Remembered destinations remain valid after pair expiry/removal, so players are not stranded merely because a physical frame disappeared.

Known destinations retain the existing bounded safe-landing and emergency world-spawn recovery if their dimension or surrounding terrain becomes unusable. The option is specifically an unknown-destination restriction, not a general ban on Overworld travel. Commands with operator permissions, other mods, beds and other transportation retain their own rules.

For a Backdoor-first pack, enable both switches. They remain independent: start-only allows the original emergency exit; require-only does not relocate anyone. Administrators can still use `/elsebase rescue`.

## Source and verification contract

The event timing, login placement, `PlayerPersisted` cloning and respawn override were checked against the resolved Minecraft 1.21.1 / NeoForge 21.1.250 sources. GameTests cover event dispatch, damage classification, queued-request revalidation, timeout, independent policy settings, first login, later login, outside summons, remembered return and respawn floor repair. Native UI checks cover translated controls and persisted TOML values. Packaging continues to exclude all development fixtures.
