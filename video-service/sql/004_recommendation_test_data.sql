USE video_db;

INSERT INTO video (
    id, title, description, cover_url, play_url, video_url, author, user_id,
    category_id, tags, duration, status, play_count, like_count,
    favorite_count, comment_count, created_at, updated_at
) VALUES
    (930301, 'UC03 Campus Music', 'Campus music test video', '',
     'https://example.test/videos/campus-music.mp4', 'videos/campus-music.mp4',
     'UC03 Author A', 930101, 3, 'campus music', 120, 'public',
     100, 0, 0, 0, '2026-08-31 09:00:00', '2026-08-31 09:00:00'),
    (930302, 'UC03 Campus Sport', 'Campus sport test video', '',
     'https://example.test/videos/campus-sport.mp4', 'videos/campus-sport.mp4',
     'UC03 Author B', 930102, 3, 'campus sport', 90, 'public',
     90, 0, 0, 0, '2026-08-30 09:00:00', '2026-08-30 09:00:00'),
    (930303, 'UC03 Travel', 'Travel test video', '',
     'https://example.test/videos/travel.mp4', 'videos/travel.mp4',
     'UC03 Author C', 930103, 4, 'travel daily', 180, 'public',
     50, 0, 0, 0, '2026-08-29 09:00:00', '2026-08-29 09:00:00'),
    (930304, 'UC03 Private', 'Private video must not be returned', '',
     'https://example.test/videos/private.mp4', 'videos/private.mp4',
     'UC03 Author D', 930104, 3, 'campus music', 60, 'private',
     99999, 999, 999, 999, '2026-08-31 10:00:00', '2026-08-31 10:00:00');

INSERT INTO view_history (
    user_id, video_id, view_count, progress_seconds, last_viewed_at
) VALUES
    (930001, 930301, 1, 30, '2026-08-31 11:00:00');
