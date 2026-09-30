# Offline rotation in OnLoopio 0.6

Settings → Cache policy offers these scenarios:

| Profile | Audio budget | Idle threshold | SD reserve | Automatic deletion |
| --- | --- | --- | --- | --- |
| Keep offline collection | Unlimited | Never | 2 GiB | Off; new downloads stop at reserve |
| Daily rotation | 96 GiB | 90 days | 2 GiB | On |
| Small card | 2 GiB | 30 days | 256 MiB | On |

Initial defaults are automatic deletion **Off**, budget 96 GiB, idle threshold
90 days and reserve 2 GiB. The threshold is used by a confirmed manual clean
even with automatic deletion disabled. Choosing a rotation profile confirms its
automatic removal behavior. Budget choices range from 512 MiB to 96 GiB, including
64 GiB, or unlimited; idle thresholds are Never/30/60/90/180/365 days; reserves are
128/256/512/1024/2048/4096 MiB. Completed audio is measured separately from the cover cache
(32 MiB) and temporary transfers. SD reserve uses actual filesystem free space.

The connected Y1's music volume `/storage/sdcard0` reports about 116 GiB total and
115 GiB available on 2026-09-28. Its 96 GiB budget leaves about 20 GiB outside the
audio budget; the 2 GiB reserve still stops downloading if other files fill the
volume. The budget is a maximum, not a preallocation. Existing saved choices are
preserved on APK updates; this Y1 was explicitly changed to 96 GiB / 2 GiB with
automatic deletion still off.

The policy first removes idle eligible files, then least recently used eligible
files until the audio budget and SD reserve are met. Listening counts after 30
seconds, or halfway through a shorter track. The last download timestamp protects
newly saved but never played music for the same idle period. Existing files adopt
their modification time on migration. Metadata remains, so removed audio can be
downloaded again.

Automatic policy runs after playback-service startup, every six hours while that
service is alive, when policy settings are applied, and before/growing during a
download. It does not wake a powered-off player. Unknown stream lengths reserve
an extra MiB as the partial grows; known lengths use the actual remaining size.
With automatic deletion off or insufficient eligible space, downloading fails
with a visible cache/reserve error instead of exceeding the configured budget.

Protect from cleanup is a persistent **track-level** flag. Selecting it on a
playlist/artist/album protects that entity's current track snapshot, including
tracks shared with other entities. Later new playlist members are not implicitly
protected by that manual snapshot action. Separately, Keep playlist offline in
0.6 protects the followed playlist's current members through playlist ownership;
future members receive that protection automatically. Stop following updates
releases that ownership while other followed playlists/manual pins remain.
Allow rotation clears the manual flag for the selected tracks, including
those shared elsewhere. The current song, predicted next song, another upcoming
queue entry and pending/failed download jobs are also excluded automatically.
Protection can make the limit unattainable; the queue then reports that condition.
Explicit Remove from player / Clear downloaded audio still remove protected
audio because they are deliberate user actions.

Clean now computes an eligible count/byte preview and defaults its confirmation
to Cancel. Execution recalculates the current policy/protection. Cleanup affects
only privately registered completed downloads for the active account in `Music`;
it does not delete imported music or another account's downloads. The ownership
record includes path, size and modification time at FAT's two-second resolution.
Paths are matched without case sensitivity. A replaced or renamed download
becomes an imported file on the next scan and is preserved by cleanup. Imported
music consumes actual free space but does not count against the download quota.
See [local music](local-music.md). Download queue
completion history records transfer success at that time, rather than promising
that a file survives a later deliberate removal/rotation.

Automatic discovery/download and reference-based protection for followed playlist
members are described in [automatic playlist synchronization](playlist-auto-sync.md).
Artist/album subscriptions, total-device limits across multiple accounts, playback
queue persistence and recommendation-based automatic music selection are future
extensions. Rotation operates on files already selected for offline downloading.
