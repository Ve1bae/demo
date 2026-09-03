USE hangyin_video;

INSERT INTO video (
    id, title, description, cover_url, play_url, video_url, author, user_id,
    category_id, tags, duration, status, play_count, like_count,
    favorite_count, comment_count, created_at, updated_at
) VALUES
    (930301, 'Benchmark Campus Music', 'Fixed monolith benchmark video', '',
     'https://example.test/videos/benchmark-music.mp4', 'benchmark/music.mp4',
     'Benchmark Author A', 930101, 3, 'campus music', 120, 'public',
     100, 0, 0, 0, '2026-08-31 09:00:00', '2026-08-31 09:00:00'),
    (930302, 'Benchmark Campus Sport', 'Fixed monolith benchmark video', '',
     'https://example.test/videos/benchmark-sport.mp4', 'benchmark/sport.mp4',
     'Benchmark Author B', 930102, 3, 'campus sport', 90, 'public',
     90, 0, 0, 0, '2026-08-30 09:00:00', '2026-08-30 09:00:00'),
    (930303, 'Benchmark Travel', 'Fixed monolith benchmark video', '',
     'https://example.test/videos/benchmark-travel.mp4', 'benchmark/travel.mp4',
     'Benchmark Author C', 930103, 4, 'travel daily', 180, 'public',
     50, 0, 0, 0, '2026-08-29 09:00:00', '2026-08-29 09:00:00');
