# Online / offline synchronization in OnLoopio 0.6

Online mode requires the configured home SSID, a successful authenticated
Navidrome ping, and no Force offline flag. The UI checks reachability about every
30 seconds while open; a failed check immediately exposes the offline view.
Leaving home Wi-Fi or forcing offline closes any active network stream. Cached
files remain playable without Wi-Fi, credentials being retransmitted or a server
request. The home SSID is not a proxy for certificate trust: HTTPS still verifies
the private CA and hostname.

A complete metadata pass reads paged search3 artists/albums/songs plus playlist
headers/details. Incremental checks fetch only changed/new playlist details and
audit all membership daily. Independent offsets enumerate every catalog collection,
including music not in a playlist. Fetched metadata and offline queue additions
commit in one SQLite transaction. Failed fetches preserve the previous snapshot. Playlist
membership preserves order and duplicates. Metadata writes run on a worker.
The SD filename index avoids scanning every server song on the UI thread. SQLite migrations 1→2→3→4→5→6→7 preserve existing account/library state.

Online screens select the current server snapshot; offline screens filter actual
completed, compatible audio files. Artists/albums/genres are derived from those
files offline, and playlists with no saved tracks disappear. A partially saved
playlist exposes just its saved tracks. Metadata for tracks removed from the
server is retained so existing offline audio stays identifiable. Account changes
reset metadata/queued downloads and select a separate hashed audio namespace.

Download actions enqueue immutable song IDs in SQLite with stable ordering and
uniqueness. Progress, safe failure descriptions and recent completed history are persisted.
Playback and downloads use separate workers: playing a track does not
wait for an offline job. Pending and failed jobs survive process death; a partial
file is restarted, never shown as playable. Home reconnect wakes an unpaused
queue. Automatic wakeups preserve pause and failed jobs; Resume retries failures. Removing an entity
cancels its jobs and removes its physical audio, including shared tracks from
other entities; metadata is retained. The playing track is stopped if selected
for removal. Clear downloaded audio also clears the queue.

MP3 and FLAC are stored unchanged. M4A/Opus and other suffixes use Navidrome's
MP3 transcoder (up to 320 kbit/s) for offline compatibility. The cache checks that
this response is actually MP3, enforces a 512 MiB limit and storage headroom,
checks known lengths, fsyncs and atomically publishes a finished file. Streaming
uses a separate bounded loopback bridge and never automatically marks a track
saved offline.

Playlist Keep offline follows future membership and queues missing files. Older
snapshot downloads and artist/album/track actions remain snapshots. Current members
of followed playlists have separate reference-based protection from rotation.
See [automatic playlist synchronization](playlist-auto-sync.md) for alarms,
charger/Wi-Fi triggers, failure handling and subscription behavior. Likes,
scrobbles, outgoing operations and full playback-queue persistence remain future
work. Covers are implemented. Track-level protection, idle-age/LRU rotation and
space/reserve limits are implemented in
[cache-policies.md](cache-policies.md).
