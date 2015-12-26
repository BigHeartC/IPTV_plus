/*
 * ff_ffplaye_options.h
 *
 * Copyright (c) 2015 Zhang Rui <bbcallen@gmail.com>
 *
 * This file is part of ijkPlayer.
 *
 * ijkPlayer is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * ijkPlayer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with ijkPlayer; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */

#ifndef FFPLAY__FF_FFPLAY_OPTIONS_H
#define FFPLAY__FF_FFPLAY_OPTIONS_H

#define OPTION_OFFSET(x) offsetof(FFPlayer, x)
#define OPTION_INT(default__, min__, max__) \
    .type = AV_OPT_TYPE_INT, \
    { .i64 = default__ }, \
    .min = min__, \
    .max = max__, \
    .flags = AV_OPT_FLAG_DECODING_PARAM

#define OPTION_CONST(default__) \
    .type = AV_OPT_TYPE_CONST, \
    { .i64 = default__ }, \
    .min = INT_MIN, \
    .max = INT_MAX, \
    .flags = AV_OPT_FLAG_DECODING_PARAM

#define OPTION_STR(default__) \
    .type = AV_OPT_TYPE_STRING, \
    { .str = default__ }, \
    .min = 0, \
    .max = 0, \
    .flags = AV_OPT_FLAG_DECODING_PARAM

static const AVOption ffp_context_options[] = {
    // original options in ffplay.c
    // FFP_MERGE: x, y, s, fs
    { "an",                             "disable audio",
        OPTION_OFFSET(audio_disable),   OPTION_INT(0, 0, 1) },
    { "vn",                             "disable video",
        OPTION_OFFSET(video_disable),   OPTION_INT(0, 0, 1) },
    // FFP_MERGE: sn, ast, vst, sst
    // TODO: ss
    { "nodisp",                         "disable graphical display",
        OPTION_OFFSET(display_disable), OPTION_INT(0, 0, 1) },
    // FFP_MERGE: f, pix_fmt, stats
    { "fast",                           "non spec compliant optimizations",
        OPTION_OFFSET(fast),            OPTION_INT(0, 0, 1) },
    // FFP_MERGE: genpts, drp, lowres, sync, autoexit, exitonkeydown, exitonmousedown
    { "loop",                           "set number of times the playback shall be looped",
        OPTION_OFFSET(loop),            OPTION_INT(1, INT_MIN, INT_MAX) },
    { "infbuf",                         "don't limit the input buffer size (useful with realtime streams)",
        OPTION_OFFSET(infinite_buffer), OPTION_INT(0, 0, 1) },
    { "framedrop",                      "drop frames when cpu is too slow",
        OPTION_OFFSET(framedrop),       OPTION_INT(0, -1, 120) },
    // FFP_MERGE: window_title
#if CONFIG_AVFILTER
    { "af",                             "audio filters",
        OPTION_OFFSET(afilters),        OPTION_STR(NULL) },
    { "vf0",                            "video filters 0",
        OPTION_OFFSET(vfilter0),        OPTION_STR(NULL) },
#endif
    { "rdftspeed",                      "rdft speed, in msecs",
        OPTION_OFFSET(rdftspeed),       OPTION_INT(0, 0, INT_MAX) },
    // FFP_MERGE: showmode, default, i, codec, acodec, scodec, vcodec
    // TODO: autorotate

    // extended options in ff_ffplay.c
    { "max-fps",                        "drop frames in video whose fps is greater than max-fps",
        OPTION_OFFSET(max_fps),         OPTION_INT(31, 0, 121) },

    { "overlay-format",                 "fourcc of overlay format",
        OPTION_OFFSET(overlay_format),  OPTION_INT(SDL_FCC_RV32, INT_MIN, INT_MAX),
        .unit = "overlay-format" },
    { "fcc-i420",                       "", 0, OPTION_CONST(SDL_FCC_I420), .unit = "overlay-format" },
    { "fcc-yv12",                       "", 0, OPTION_CONST(SDL_FCC_YV12), .unit = "overlay-format" },
    { "fcc-rv16",                       "", 0, OPTION_CONST(SDL_FCC_RV16), .unit = "overlay-format" },
    { "fcc-rv24",                       "", 0, OPTION_CONST(SDL_FCC_RV24), .unit = "overlay-format" },
    { "fcc-rv32",                       "", 0, OPTION_CONST(SDL_FCC_RV32), .unit = "overlay-format" },

    { "start-on-prepared",                  "automatically start playing on prepared",
        OPTION_OFFSET(start_on_prepared),   OPTION_INT(1, 0, 1) },

    { "video-pictq-size",                   "max picture queue frame count",
        OPTION_OFFSET(pictq_size),          OPTION_INT(VIDEO_PICTURE_QUEUE_SIZE_DEFAULT,
                                                       VIDEO_PICTURE_QUEUE_SIZE_MIN,
                                                       VIDEO_PICTURE_QUEUE_SIZE_MAX) },

    { "max-buffer-size",                    "max buffer size should be pre-read",
        OPTION_OFFSET(max_buffer_size),     OPTION_INT(MAX_QUEUE_SIZE, 0, MAX_QUEUE_SIZE) },

    { "packet-buffering",                   "pause output until enough packets have been read after stalling",
        OPTION_OFFSET(packet_buffering),    OPTION_INT(1, 0, 1) },
    { "sync-av-start",                      "synchronise a/v start time",
        OPTION_OFFSET(sync_av_start),       OPTION_INT(1, 0, 1) },
    { "iformat",                            "force format",
        OPTION_OFFSET(iformat_name),        OPTION_STR(NULL) },
    { "min-frames",                         "minimal frames to stop pre-reading",
        OPTION_OFFSET(min_frames),          OPTION_INT(DEFAULT_MIN_FRAMES, MIN_MIN_FRAMES, MAX_MIN_FRAMES) },

    // iOS only options
    { "videotoolbox",                       "VideoToolbox: enable",
        OPTION_OFFSET(videotoolbox),        OPTION_INT(0, 0, 1) },
    { "videotoolbox-max-frame-width",       "VideoToolbox: max width of output frame",
        OPTION_OFFSET(vtb_max_frame_width), OPTION_INT(0, 0, INT_MAX) },
    { "videotoolbox-async",                 "VideoToolbox: use kVTDecodeFrame_EnableAsynchronousDecompression()",
        OPTION_OFFSET(vtb_async),           OPTION_INT(0, 0, 1) },
    { "videotoolbox-wait-async",            "VideoToolbox: call VTDecompressionSessionWaitForAsynchronousFrames()",
        OPTION_OFFSET(vtb_wait_async),      OPTION_INT(1, 0, 1) },

    // Android only options
    { "mediacodec",                             "MediaCodec: enable H264 (deprecated by 'mediacodec-avc')",
        OPTION_OFFSET(mediacodec_avc),          OPTION_INT(0, 0, 1) },
    { "mediacodec-auto-rotate",                 "MediaCodec: auto rotate frame depending on meta",
        OPTION_OFFSET(mediacodec_auto_rotate),  OPTION_INT(0, 0, 1) },
    { "mediacodec-all-videos",                  "MediaCodec: enable all videos",
        OPTION_OFFSET(mediacodec_all_videos),   OPTION_INT(0, 0, 1) },
    { "mediacodec-avc",                         "MediaCodec: enable H264",
        OPTION_OFFSET(mediacodec_avc),          OPTION_INT(0, 0, 1) },
    { "mediacodec-hevc",                        "MediaCodec: enable HEVC",
        OPTION_OFFSET(mediacodec_hevc),         OPTION_INT(0, 0, 1) },

    { "opensles",                           "OpenSL ES: enable",
        OPTION_OFFSET(opensles),            OPTION_INT(0, 0, 1) },
    
    { NULL }
};

#undef OPTION_STR
#undef OPTION_CONST
#undef OPTION_INT
#undef OPTION_OFFSET

#endif
