# Automatic playlist synchronization in OnLoopio 0.6

Settings → Playlist synchronization is operated with the physical wheel:

| Setting | Initial value | Choices / behavior |
| --- | --- | --- |
| Automatic playlist sync | On | Disables automatic triggers together; Check now still works |
| Check every | 15 minutes | Off / 5 / 15 / 30 / 60 / 120 / 360 minutes |
| Check on charger connection | On | Android power-connected broadcast |
| Check on charger removal | On | Android power-disconnected broadcast |
| Check on home Wi-Fi connection | On | Transition into the configured home Wi-Fi |
| Offline playlists | None automatically enrolled | Choose which playlists to keep updated on SD |
| Check now | Manual | Immediate incremental check, including when automatic sync is Off |

The screen shows the last successful check and current result (playlist count,
refreshed lists, added download jobs), or a waiting/failure status. Existing saved
pre-0.6 audio remains saved; old snapshot downloads are not silently converted
into permanent offline subscriptions.

An API-17 elapsed-time repeating AlarmManager alarm checks while the screen is
asleep and survives process death. App start restores the alarm; boot resets its
schedule. Clock adjustments do not change the interval, and missed checks do not
run as a backlog. Nothing runs while the player is powered off. Charger/Wi-Fi
events start the same service. A 1.5-second delay combines a burst of events into
one check; requests arriving during a check are serialized into a subsequent one.
CPU/Wi-Fi locks cover the check and are released afterwards. Network work has a
four-minute guard deadline and bounded HTTP timeouts.

Every check requires home Wi-Fi, no Force offline flag, the current account, and
successful authenticated HTTPS reads. The service checks that gate again between
requests and before publication. It compares playlist ID, changed timestamp,
name, song count and duration; new, changed, uncached or explicitly refreshed
playlists fetch their full ordered membership. Missing changed timestamps cause
a full detail fetch on each check. A daily audit fetches all details even when
headers appear unchanged. The full artist/album/track catalog is refreshed when
missing, daily, or through the existing manual catalog-refresh action.
It is also refreshed immediately when updated playlist membership introduces a
song absent from the local catalog, keeping Artists/Albums/Tracks in step.

All fetched headers, details, optional catalog, download additions and successful
check time commit in one SQLite transaction. A network/response/account failure
keeps the previous snapshot. Order and repeated entries are retained. Remote
deletion removes playlist metadata/subscription, preserving downloaded audio and
song metadata. No Navidrome or AudioMuse playlist is modified by OnLoopio.

Keep playlist offline is available in a playlist's held-center menu, its detail
screen, and Settings → Playlist synchronization → Offline playlists. It follows
the existing playlist ID across AudioMuse updates. Every successful check adds
missing current members to the persistent download queue. Shared/duplicate song
IDs use one job/file. Automatic checks respect an existing download pause and do
not repeatedly retry failed transfers; Resume / retry failed remains explicit.
Queued tracks previously selected for downloading stay queued if removed from a
playlist, so automatic synchronization does not cancel separate manual jobs.

Current members of followed playlists are protected from cache rotation by their
own playlist references, independently of manual Protect from cleanup flags.
Removal from one list releases that list's protection; another followed list or
manual pin can still protect the song. Old downloaded tracks are not deleted by
a playlist update; ordinary cache policy may later remove eligible files.
Stop following updates retains saved audio and current queued jobs. Removing an
entire playlist from the player stops following it before deleting selected audio.
Removing a single still-followed member allows the next check to download it again.
Clear downloaded audio stops all playlist subscriptions to avoid immediate refill.

Keep-offline subscriptions can exceed the available cache budget; downloads then
report the cache/reserve failure without deleting protected music. Increase the
budget, release subscriptions or manually remove selected audio as appropriate.
Artist/album/track download actions remain snapshots; automatic recommendations,
scrobbles and full playback-queue restoration are separate future work.
