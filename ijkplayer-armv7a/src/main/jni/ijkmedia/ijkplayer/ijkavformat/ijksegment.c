/*
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

#include <assert.h>
#include "libavformat/avformat.h"
#include "libavformat/url.h"
#include "libavutil/avstring.h"
#include "libavutil/log.h"
#include "libavutil/opt.h"

#include "ijkplayer/ijkavutil/opt.h"
#include "ijkavformat.h"

typedef struct Context {
    AVClass        *class;
    URLContext     *inner;

    /* options */
    int64_t         opaque;
    int             segment_index;
    char           *http_hook;
} Context;

static void *ijksegment_get_opaque(URLContext *h) {
    Context *c = h->priv_data;
#ifdef __GNUC__
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wint-to-pointer-cast"
#endif
    return (void *)c->opaque;
#ifdef __GNUC__
#pragma GCC diagnostic pop
#endif
}

static int ijksegment_open(URLContext *h, const char *arg, int flags, AVDictionary **options)
{
    Context *c = h->priv_data;
    IJKAVInject_OnUrlOpenData inject_data = {0};
    IjkAVInjectCallback inject_callback = ijkav_get_inject_callback();
    int ret = -1;
    void *opaque = ijksegment_get_opaque(h);
    assert(opaque);

    if (!c->opaque) {
        av_log(h, AV_LOG_ERROR, "null opaque\n");
        return AVERROR_EXTERNAL;
    }

    if (!inject_callback) {
        av_log(h, AV_LOG_ERROR, "null inject_callback\n");
        return AVERROR_EXTERNAL;
    }

    av_strstart(arg, "ijksegment:", &arg);
    if (!arg || !*arg)
        return AVERROR_EXTERNAL;

    inject_data.size = sizeof(inject_data);
    inject_data.segment_index = (int)strtol(arg, NULL, 0);
    strlcpy(inject_data.url,    arg,    sizeof(inject_data.url));

    if (opaque && inject_callback &&
        inject_data.segment_index < 0) {
        ret = AVERROR_EXTERNAL;
        goto fail;
    }

    ret = inject_callback(opaque, IJKAVINJECT_CONCAT_RESOLVE_SEGMENT, &inject_data, sizeof(inject_data));
    if (ret || !inject_data.url[0]) {
        ret = AVERROR_EXIT;
        goto fail;
    }

    av_dict_set_int(options, "ijkinject-opaque",        c->opaque, 0);
    av_dict_set_int(options, "ijkinject-segment-index", c->segment_index, 0);

    ret = ffurl_open(&c->inner, inject_data.url, flags, &h->interrupt_callback, options);
    if (ret)
        goto fail;

    return 0;
fail:
    return ret;
}

static int ijksegment_close(URLContext *h)
{
    Context *c = h->priv_data;

    return ffurl_close(c->inner);
}

static int ijksegment_read(URLContext *h, unsigned char *buf, int size)
{
    Context *c = h->priv_data;

    return ffurl_read(c->inner, buf, size);
}

static int64_t ijksegment_seek(URLContext *h, int64_t pos, int whence)
{
    Context *c = h->priv_data;

    return ffurl_seek(c->inner, pos, whence);
}

#define OFFSET(x) offsetof(Context, x)
#define D AV_OPT_FLAG_DECODING_PARAM

static const AVOption options[] = {
    { "ijkinject-opaque",           "private data of user, passed with custom callback",
        OFFSET(opaque),             IJKAV_OPTION_INT64(0, INT64_MIN, INT64_MAX) },
    { "ijkinject-segment-index",    "segment index of current url",
        OFFSET(segment_index),      IJKAV_OPTION_INT(0, 0, INT_MAX) },
    { NULL }
};

#undef D
#undef OFFSET

static const AVClass ijksegment_context_class = {
    .class_name = "Inject",
    .item_name  = av_default_item_name,
    .option     = options,
    .version    = LIBAVUTIL_VERSION_INT,
};

URLProtocol ijkff_ijksegment_protocol = {
    .name                = "ijksegment",
    .url_open2           = ijksegment_open,
    .url_read            = ijksegment_read,
    .url_seek            = ijksegment_seek,
    .url_close           = ijksegment_close,
    .priv_data_size      = sizeof(Context),
    .priv_data_class     = &ijksegment_context_class,
};
