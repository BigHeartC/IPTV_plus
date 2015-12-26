/*
 * ffpipenode_android_mediacodec_vdec.c
 *
 * Copyright (c) 2014 Zhang Rui <bbcallen@gmail.com>
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

#include "ffpipenode_android_mediacodec_vdec.h"
#include "ffpipeline_android.h"
#include "../../ff_ffpipenode.h"
#include "../../ff_ffplay.h"
#include "../../ff_ffplay_debug.h"
#include "ijksdl/android/ijksdl_android_jni.h"
#include "ijksdl/android/ijksdl_codec_android_mediaformat_java.h"
#include "ijksdl/android/ijksdl_codec_android_mediacodec_java.h"
#include "ijksdl/android/ijksdl_vout_android_nativewindow.h"
#include "ijksdl/android/ijksdl_vout_overlay_android_mediacodec.h"
#include "h264_nal.h"
#include "hevc_nal.h"

#define AMC_USE_AVBITSTREAM_FILTER 0
#ifndef AMCTRACE
//#define AMCTRACE(...)
#define AMCTRACE ALOGE
#endif

#define AMC_INPUT_TIMEOUT_US  (1000000)
#define AMC_OUTPUT_TIMEOUT_US (1000000)

#define MAX_FAKE_FRAMES (2)

typedef struct AMC_Buf_Out {
    int port;
    int acodec_serial;
    SDL_AMediaCodecBufferInfo info;
    double pts;
} AMC_Buf_Out;

typedef struct IJKFF_Pipenode_Opaque {
    FFPlayer                 *ffp;
    IJKFF_Pipeline           *pipeline;
    Decoder                  *decoder;
    SDL_Vout                 *weak_vout;

    ijkmp_mediacodecinfo_context mcc;

    jobject                   jsurface;
    SDL_AMediaFormat         *input_aformat;
    SDL_AMediaCodec          *acodec;
    SDL_AMediaFormat         *output_aformat;
    char                      acodec_name[128];
    int                       frame_width;
    int                       frame_height;
    int                       frame_rotate_degrees;

    AVCodecContext           *avctx; // not own
    AVBitStreamFilterContext *bsfc;  // own

#if AMC_USE_AVBITSTREAM_FILTER
    uint8_t                  *orig_extradata;
    int                       orig_extradata_size;
#else
    size_t                    nal_size;
#endif

    SDL_Thread               _enqueue_thread;
    SDL_Thread               *enqueue_thread;

    SDL_mutex                *acodec_mutex;
    SDL_cond                 *acodec_cond;

    volatile bool             acodec_flush_request;
    volatile bool             acodec_reconfigure_request;

    SDL_mutex                *acodec_first_dequeue_output_mutex;
    SDL_cond                 *acodec_first_dequeue_output_cond;
    volatile bool             acodec_first_dequeue_output_request;

    int                       fake_pictq_serial;
    PacketQueue               fake_pictq;

    int                       input_packet_count;
    int                       input_error_count;
    int                       output_error_count;

    bool                      quirk_reconfigure_with_new_codec;

    int                       n_buf_out;
    AMC_Buf_Out               *amc_buf_out;
    int                       off_buf_out;
    double                    last_queued_pts;

    SDL_SpeedSampler          sampler;
} IJKFF_Pipenode_Opaque;

static SDL_AMediaCodec *create_codec_l(JNIEnv *env, IJKFF_Pipenode *node)
{
    IJKFF_Pipenode_Opaque        *opaque   = node->opaque;
    ijkmp_mediacodecinfo_context *mcc      = &opaque->mcc;
    SDL_AMediaCodec              *acodec   = NULL;

    acodec = SDL_AMediaCodecJava_createByCodecName(env, mcc->codec_name);
    if (acodec) {
        strncpy(opaque->acodec_name, mcc->codec_name, sizeof(opaque->acodec_name) / sizeof(*opaque->acodec_name));
        opaque->acodec_name[sizeof(opaque->acodec_name) / sizeof(*opaque->acodec_name) - 1] = 0;
    }

#if 0
    if (!acodec)
        acodec = SDL_AMediaCodecJava_createDecoderByType(env, mcc->mime_type);
#endif

    if (acodec) {
        // QUIRK: always recreate MediaCodec for reconfigure
        opaque->quirk_reconfigure_with_new_codec = true;
        /*-
        if (0 == strncasecmp(mcc->codec_name, "OMX.TI.DUCATI1.", 15)) {
            opaque->quirk_reconfigure_with_new_codec = true;
        }
        */
        /* delaying output makes it possible to correct frame order, hopefully */
        if (0 == strncasecmp(mcc->codec_name, "OMX.TI.DUCATI1.", 15)) {
            /* this is the only acceptable value on Nexus S */
            opaque->n_buf_out = 1;
            ALOGD("using buffered output for %s", mcc->codec_name);
        }
    }

    return acodec;
}

static int reconfigure_codec_l(JNIEnv *env, IJKFF_Pipenode *node, jobject new_surface)
{
    IJKFF_Pipenode_Opaque *opaque   = node->opaque;
    int                    ret      = 0;
    sdl_amedia_status_t    amc_ret  = 0;
    jobject                prev_jsurface = NULL;

    prev_jsurface = opaque->jsurface;
    if (new_surface) {
        opaque->jsurface = (*env)->NewGlobalRef(env, new_surface);
        if (JJK_ExceptionCheck__catchAll(env) || !opaque->jsurface)
            goto fail;
    } else {
        opaque->jsurface = NULL;
    }

    SDL_JNI_DeleteGlobalRefP(env, &prev_jsurface);

    if (!opaque->acodec) {
        opaque->acodec = create_codec_l(env, node);
        if (!opaque->acodec) {
            ALOGE("%s:open_video_decoder: create_codec failed\n", __func__);
            ret = -1;
            goto fail;
        }
    }

    if (SDL_AMediaCodec_isConfigured(opaque->acodec)) {
        if (opaque->acodec) {
            if (SDL_AMediaCodec_isStarted(opaque->acodec)) {
                SDL_VoutAndroid_invalidateAllBuffers(opaque->weak_vout);
                SDL_AMediaCodec_stop(opaque->acodec);
            }
            if (opaque->quirk_reconfigure_with_new_codec) {
                ALOGI("quirk: reconfigure with new codec");
                SDL_AMediaCodec_decreaseReferenceP(&opaque->acodec);

                opaque->acodec = create_codec_l(env, node);
                if (!opaque->acodec) {
                    ALOGE("%s:open_video_decoder: create_codec failed\n", __func__);
                    ret = -1;
                    goto fail;
                }
            }
        }

        assert(opaque->weak_vout);
    }

    amc_ret = SDL_AMediaCodec_configure_surface(env, opaque->acodec, opaque->input_aformat, opaque->jsurface, NULL, 0);
    if (amc_ret != SDL_AMEDIA_OK) {
        ALOGE("%s:configure_surface: failed\n", __func__);
        ret = -1;
        goto fail;
    }

    SDL_AMediaCodec_start(opaque->acodec);
    opaque->acodec_first_dequeue_output_request = true;
    ALOGI("%s:new acodec: %p\n", __func__, opaque->acodec);
    SDL_VoutAndroid_setAMediaCodec(opaque->weak_vout, opaque->acodec);
fail:
    return ret;
}

#if 0
static int reconfigure_codec(JNIEnv *env, IJKFF_Pipenode *node)
{
    IJKFF_Pipenode_Opaque *opaque = node->opaque;
    SDL_LockMutex(opaque->acodec_mutex);
    int ret = reconfigure_codec_l(env, node);
    SDL_UnlockMutex(opaque->acodec_mutex);
    return ret;
}
#endif

static int amc_fill_frame(
    IJKFF_Pipenode            *node,
    AVFrame                   *frame,
    int                       *got_frame,
    int                        output_buffer_index,
    int                        acodec_serial,
    SDL_AMediaCodecBufferInfo *buffer_info)
{
    IJKFF_Pipenode_Opaque *opaque     = node->opaque;
    FFPlayer              *ffp        = opaque->ffp;
    VideoState            *is         = ffp->is;

    frame->opaque = SDL_VoutAndroid_obtainBufferProxy(opaque->weak_vout, acodec_serial, output_buffer_index);
    if (!frame->opaque)
        goto fail;

    frame->width  = opaque->frame_width;
    frame->height = opaque->frame_height;
    frame->format = SDL_FCC__AMC;
    frame->sample_aspect_ratio = opaque->avctx->sample_aspect_ratio;
    frame->pts    = av_rescale_q(buffer_info->presentationTimeUs, AV_TIME_BASE_Q, is->video_st->time_base);
    if (frame->pts < 0)
        frame->pts = AV_NOPTS_VALUE;

    *got_frame = 1;
    return 0;
fail:
    *got_frame = 0;
    return -1;
}

static int amc_fill_frame_fake(IJKFF_Pipenode *node, AVFrame *frame, int *got_frame, AVPacket *pkt)
{
    IJKFF_Pipenode_Opaque *opaque     = node->opaque;
    FFPlayer              *ffp        = opaque->ffp;
    VideoState            *is         = ffp->is;
    int64_t time_stamp = pkt->pts;
    if (!time_stamp && pkt->dts)
        time_stamp = pkt->dts;
    if (time_stamp > 0) {
        time_stamp = av_rescale_q(time_stamp, is->video_st->time_base, AV_TIME_BASE_Q);
    } else {
        time_stamp = 0;
    }

    SDL_AMediaCodecBufferInfo buffer_info;
    memset(&buffer_info, 0, sizeof(buffer_info));
    buffer_info.presentationTimeUs = time_stamp;

    return amc_fill_frame(node, frame, got_frame, -1, 0, &buffer_info);
}

static int amc_decode_picture_fake(IJKFF_Pipenode *node, uint32_t timeout_milli)
{
    IJKFF_Pipenode_Opaque *opaque   = node->opaque;
    FFPlayer              *ffp      = opaque->ffp;
    VideoState            *is       = ffp->is;
    Decoder               *d        = &is->viddec;
    int                    ret      = 0;

    PacketQueue *q = &opaque->fake_pictq;
    SDL_LockMutex(q->mutex);
    if (!q->abort_request && q->nb_packets > MAX_FAKE_FRAMES) {
        SDL_CondWaitTimeout(q->cond, q->mutex, timeout_milli);
    }
    SDL_UnlockMutex(q->mutex);

    if (q->abort_request) {
        ret = -1;
        goto fail;
    } else {
        ffp_packet_queue_put(&opaque->fake_pictq, &d->pkt);
        av_init_packet(&d->pkt); // avoid duplicated free on packet
        d->packet_pending = 0;
        ret = 0;
        goto fail;
    }

fail:
    return ret;
}

static int feed_input_buffer(JNIEnv *env, IJKFF_Pipenode *node, int64_t timeUs, int *enqueue_count)
{
    IJKFF_Pipenode_Opaque *opaque   = node->opaque;
    FFPlayer              *ffp      = opaque->ffp;
    IJKFF_Pipeline        *pipeline = opaque->pipeline;
    VideoState            *is       = ffp->is;
    Decoder               *d        = &is->viddec;
    PacketQueue           *q        = d->queue;
    sdl_amedia_status_t    amc_ret  = 0;
    int                    ret      = 0;
    ssize_t  input_buffer_index = 0;
    uint8_t* input_buffer_ptr   = NULL;
    size_t   input_buffer_size  = 0;
    size_t   copy_size          = 0;
    int64_t  time_stamp         = 0;

    if (enqueue_count)
        *enqueue_count = 0;

    if (d->queue->abort_request) {
        ret = 0;
        goto fail;
    }

    opaque->avctx = opaque->decoder->avctx;

    if (!d->packet_pending || d->queue->serial != d->pkt_serial) {
#if AMC_USE_AVBITSTREAM_FILTER
#else
        H264ConvertState convert_state = {0, 0};
#endif
        AVPacket pkt;
        do {
            if (d->queue->nb_packets == 0)
                SDL_CondSignal(d->empty_queue_cond);
            if (ffp_packet_queue_get_or_buffering(ffp, d->queue, &pkt, &d->pkt_serial, &d->finished) < 0) {
                ret = -1;
                goto fail;
            }
            if (ffp_is_flush_packet(&pkt) || opaque->acodec_flush_request) {
                // request flush before lock, or never get mutex
                opaque->acodec_flush_request = true;
                SDL_LockMutex(opaque->acodec_mutex);
                if (SDL_AMediaCodec_isStarted(opaque->acodec)) {
                    if (opaque->input_packet_count > 0) {
                        // flush empty queue cause error on OMX.SEC.AVC.Decoder (Nexus S)
                        SDL_VoutAndroid_invalidateAllBuffers(opaque->weak_vout);
                        SDL_AMediaCodec_flush(opaque->acodec);
                        opaque->input_packet_count = 0;
                    }
                    // If codec is configured in synchronous mode, codec will resume automatically
                    // SDL_AMediaCodec_start(opaque->acodec);
                }
                opaque->acodec_flush_request = false;
                SDL_CondSignal(opaque->acodec_cond);
                SDL_UnlockMutex(opaque->acodec_mutex);
                d->finished = 0;
                d->next_pts = d->start_pts;
                d->next_pts_tb = d->start_pts_tb;
            }
        } while (ffp_is_flush_packet(&pkt) || d->queue->serial != d->pkt_serial);
        av_free_packet(&d->pkt);
        d->pkt_temp = d->pkt = pkt;
        d->packet_pending = 1;
#if AMC_USE_AVBITSTREAM_FILTER
        // d->pkt_temp->data could be allocated by av_bitstream_filter_filter
        if (d->bfsc_ret > 0) {
            if (d->bfsc_data)
                av_freep(&d->bfsc_data);
            d->bfsc_ret = 0;
        }
        d->bfsc_ret =
            av_bitstream_filter_filter(opaque->bsfc, opaque->avctx, NULL, &d->pkt_temp.data, &d->pkt_temp.size,
                                       d->pkt.data, d->pkt.size, d->pkt.flags & AV_PKT_FLAG_KEY);
        if (d->bfsc_ret > 0) {
            d->bfsc_data = d->pkt_temp.data;
        } else if (d->bfsc_ret < 0) {
            ALOGE("%s: av_bitstream_filter_filter failed\n", __func__);
            ret = -1;
            goto fail;
        }

        if (d->pkt_temp.size == d->pkt.size + opaque->avctx->extradata_size) {
            d->pkt_temp.data += opaque->avctx->extradata_size;
            d->pkt_temp.size  = d->pkt.size;
        }

        AMCTRACE("bsfc->filter(%d): %p[%d] -> %p[%d]", d->bfsc_ret, d->pkt.data, (int)d->pkt.size, d->pkt_temp.data, (int)d->pkt_temp.size);
#else
#if 0
        AMCTRACE("raw [%d][%d] %02x%02x%02x%02x%02x%02x%02x%02x", (int)d->pkt_temp.size,
            (int)opaque->nal_size,
            d->pkt_temp.data[0],
            d->pkt_temp.data[1],
            d->pkt_temp.data[2],
            d->pkt_temp.data[3],
            d->pkt_temp.data[4],
            d->pkt_temp.data[5],
            d->pkt_temp.data[6],
            d->pkt_temp.data[7]);
#endif
    if (opaque->avctx->codec_id == AV_CODEC_ID_H264 || opaque->avctx->codec_id == AV_CODEC_ID_HEVC) {
        convert_h264_to_annexb(d->pkt_temp.data, d->pkt_temp.size, opaque->nal_size, &convert_state);
        int64_t time_stamp = d->pkt_temp.pts;
        if (!time_stamp && d->pkt_temp.dts)
            time_stamp = d->pkt_temp.dts;
        if (time_stamp > 0) {
            time_stamp = av_rescale_q(time_stamp, is->video_st->time_base, AV_TIME_BASE_Q);
        } else {
            time_stamp = 0;
        }
    }
#if 0
        AMCTRACE("input[%d][%d][%lld,%lld (%d, %d) -> %lld] %02x%02x%02x%02x%02x%02x%02x%02x", (int)d->pkt_temp.size,
            (int)opaque->nal_size,
            (int64_t)d->pkt_temp.pts,
            (int64_t)d->pkt_temp.dts,
            (int)is->video_st->time_base.num,
            (int)is->video_st->time_base.den,
            (int64_t)time_stamp,
            d->pkt_temp.data[0],
            d->pkt_temp.data[1],
            d->pkt_temp.data[2],
            d->pkt_temp.data[3],
            d->pkt_temp.data[4],
            d->pkt_temp.data[5],
            d->pkt_temp.data[6],
            d->pkt_temp.data[7]);
#endif
#endif
    }

    if (d->pkt_temp.data) {
        // reconfigure surface if surface changed
        // NULL surface cause no display
        if (ffpipeline_is_surface_need_reconfigure_l(pipeline)) {
            jobject new_surface = NULL;

            // request reconfigure before lock, or never get mutex
            ffpipeline_lock_surface(pipeline);
            ffpipeline_set_surface_need_reconfigure_l(pipeline, false);
            new_surface = ffpipeline_get_surface_as_global_ref_l(env, pipeline);
            ffpipeline_unlock_surface(pipeline);

            if (opaque->jsurface == new_surface ||
                (opaque->jsurface && new_surface && (*env)->IsSameObject(env, new_surface, opaque->jsurface))) {
                ALOGI("%s: same surface, reuse previous surface\n", __func__);
                JJK_DeleteGlobalRef__p(env, &new_surface);
            } else {
                opaque->acodec_reconfigure_request = true;
                SDL_LockMutex(opaque->acodec_mutex);
                ret = reconfigure_codec_l(env, node, new_surface);
                opaque->acodec_reconfigure_request = false;
                SDL_CondSignal(opaque->acodec_cond);
                SDL_UnlockMutex(opaque->acodec_mutex);

                JJK_DeleteGlobalRef__p(env, &new_surface);

                if (ret != 0) {
                    ALOGE("%s: reconfigure_codec failed\n", __func__);
                    ret = 0;
                    goto fail;
                }

                SDL_LockMutex(opaque->acodec_first_dequeue_output_mutex);
                while (!q->abort_request &&
                    !opaque->acodec_reconfigure_request &&
                    !opaque->acodec_flush_request &&
                    opaque->acodec_first_dequeue_output_request) {
                    SDL_CondWaitTimeout(opaque->acodec_first_dequeue_output_cond, opaque->acodec_first_dequeue_output_mutex, 1000);
                }
                SDL_UnlockMutex(opaque->acodec_first_dequeue_output_mutex);

                if (q->abort_request || opaque->acodec_reconfigure_request || opaque->acodec_flush_request) {
                    ret = 0;
                    goto fail;
                }
            }
        }

#if 0
        // no need to decode without surface
        if (!opaque->jsurface) {
            ret = amc_decode_picture_fake(node, 1000);
            goto fail;
        }
#endif

        input_buffer_index = SDL_AMediaCodec_dequeueInputBuffer(opaque->acodec, timeUs);
        if (input_buffer_index < 0) {
            if (SDL_AMediaCodec_isInputBuffersValid(opaque->acodec)) {
                // timeout
                ret = 0;
                goto fail;
            } else {
                // exception
                ret = amc_decode_picture_fake(node, 1000);
                goto fail;
            }
        } else {
            // remove all fake pictures
            if (opaque->fake_pictq.nb_packets > 0)
                ffp_packet_queue_flush(&opaque->fake_pictq);
        }

        input_buffer_ptr = SDL_AMediaCodec_getInputBuffer(opaque->acodec, input_buffer_index, &input_buffer_size);
        if (!input_buffer_ptr) {
            ALOGE("%s: SDL_AMediaCodec_getInputBuffer failed\n", __func__);
            ret = -1;
            goto fail;
        }

        copy_size = FFMIN(input_buffer_size, d->pkt_temp.size);
        memcpy(input_buffer_ptr, d->pkt_temp.data, copy_size);

        time_stamp = d->pkt_temp.pts;
        if (!time_stamp && d->pkt_temp.dts)
            time_stamp = d->pkt_temp.dts;
        if (time_stamp > 0) {
            time_stamp = av_rescale_q(time_stamp, is->video_st->time_base, AV_TIME_BASE_Q);
        } else {
            time_stamp = 0;
        }
        // ALOGE("queueInputBuffer, %lld\n", time_stamp);
        amc_ret = SDL_AMediaCodec_queueInputBuffer(opaque->acodec, input_buffer_index, 0, copy_size, time_stamp, 0);
        if (amc_ret != SDL_AMEDIA_OK) {
            ALOGE("%s: SDL_AMediaCodec_getInputBuffer failed\n", __func__);
            ret = -1;
            goto fail;
        }
        // ALOGE("%s: queue %d/%d", __func__, (int)copy_size, (int)input_buffer_size);
        opaque->input_packet_count++;
        if (enqueue_count)
            ++*enqueue_count;
    }

    if (input_buffer_size < 0) {
        d->packet_pending = 0;
    } else {
        d->pkt_temp.dts =
        d->pkt_temp.pts = AV_NOPTS_VALUE;
        if (d->pkt_temp.data) {
            d->pkt_temp.data += copy_size;
            d->pkt_temp.size -= copy_size;
            if (d->pkt_temp.size <= 0)
                d->packet_pending = 0;
        } else {
            // FIXME: detect if decode finished
            // if (!got_frame) {
                d->packet_pending = 0;
                d->finished = d->pkt_serial;
            // }
        }
    }

fail:
    return ret;
}

static int enqueue_thread_func(void *arg)
{
    JNIEnv                *env      = NULL;
    IJKFF_Pipenode        *node     = arg;
    IJKFF_Pipenode_Opaque *opaque   = node->opaque;
    FFPlayer              *ffp      = opaque->ffp;
    VideoState            *is       = ffp->is;
    Decoder               *d        = &is->viddec;
    PacketQueue           *q        = d->queue;
    int                    ret      = -1;
    int                    dequeue_count = 0;

    if (JNI_OK != SDL_JNI_SetupThreadEnv(&env)) {
        ALOGE("%s: SetupThreadEnv failed\n", __func__);
        goto fail;
    }

    while (!q->abort_request) {
        ret = feed_input_buffer(env, node, AMC_INPUT_TIMEOUT_US, &dequeue_count);
        if (ret != 0) {
            goto fail;
        }
    }

    ret = 0;
fail:
    ffp_packet_queue_abort(&opaque->fake_pictq);
    ALOGI("MediaCodec: %s: exit: %d", __func__, ret);
    return ret;
}

static double pts_from_buffer_info(IJKFF_Pipenode *node, SDL_AMediaCodecBufferInfo *buffer_info)
{
    IJKFF_Pipenode_Opaque *opaque     = node->opaque;
    FFPlayer              *ffp        = opaque->ffp;
    VideoState            *is         = ffp->is;
    AVRational             tb         = is->video_st->time_base;
    int64_t amc_pts = av_rescale_q(buffer_info->presentationTimeUs, AV_TIME_BASE_Q, is->video_st->time_base);
    double pts = amc_pts < 0 ? NAN : amc_pts * av_q2d(tb);

    return pts;
}

/* it's OK here */
static void sort_amc_buf_out(AMC_Buf_Out *buf_out, int size) {
    AMC_Buf_Out *a, *b, tmp;
    int i, j;

    for (i = 0; i < size; i++) {
        for (j = i + 1; j < size; j++) {
            a = buf_out + i;
            b = buf_out + j;
            if (a->pts < b->pts) {
                tmp = *a;
                *a = *b;
                *b = tmp;
            }
        }
    }
}

static int drain_output_buffer_l(JNIEnv *env, IJKFF_Pipenode *node, int64_t timeUs, int *dequeue_count, AVFrame *frame, int *got_frame)
{
    IJKFF_Pipenode_Opaque *opaque   = node->opaque;
    FFPlayer              *ffp      = opaque->ffp;
    int                    ret      = 0;
    SDL_AMediaCodecBufferInfo bufferInfo;
    ssize_t                   output_buffer_index = 0;

    if (dequeue_count)
        *dequeue_count = 0;

    if (JNI_OK != SDL_JNI_SetupThreadEnv(&env)) {
        ALOGE("%s:create: SetupThreadEnv failed\n", __func__);
        return -1;
    }

    output_buffer_index = SDL_AMediaCodec_dequeueOutputBuffer(opaque->acodec, &bufferInfo, timeUs);
    if (output_buffer_index == AMEDIACODEC__INFO_OUTPUT_BUFFERS_CHANGED) {
        ALOGI("AMEDIACODEC__INFO_OUTPUT_BUFFERS_CHANGED\n");
        // continue;
    } else if (output_buffer_index == AMEDIACODEC__INFO_OUTPUT_FORMAT_CHANGED) {
        ALOGI("AMEDIACODEC__INFO_OUTPUT_FORMAT_CHANGED\n");
        SDL_AMediaFormat_deleteP(&opaque->output_aformat);
        opaque->output_aformat = SDL_AMediaCodec_getOutputFormat(opaque->acodec);
        if (opaque->output_aformat) {
            int width        = 0;
            int height       = 0;
            int color_format = 0;
            int stride       = 0;
            int slice_height = 0;
            int crop_left    = 0;
            int crop_top     = 0;
            int crop_right   = 0;
            int crop_bottom  = 0;

            SDL_AMediaFormat_getInt32(opaque->output_aformat, "width",          &width);
            SDL_AMediaFormat_getInt32(opaque->output_aformat, "height",         &height);
            SDL_AMediaFormat_getInt32(opaque->output_aformat, "color-format",   &color_format);

            SDL_AMediaFormat_getInt32(opaque->output_aformat, "stride",         &stride);
            SDL_AMediaFormat_getInt32(opaque->output_aformat, "slice-height",   &slice_height);
            SDL_AMediaFormat_getInt32(opaque->output_aformat, "crop-left",      &crop_left);
            SDL_AMediaFormat_getInt32(opaque->output_aformat, "crop-top",       &crop_top);
            SDL_AMediaFormat_getInt32(opaque->output_aformat, "crop-right",     &crop_right);
            SDL_AMediaFormat_getInt32(opaque->output_aformat, "crop-bottom",    &crop_bottom);

            // TI decoder could crash after reconfigure
            // ffp_notify_msg3(ffp, FFP_MSG_VIDEO_SIZE_CHANGED, width, height);
            // opaque->frame_width  = width;
            // opaque->frame_height = height;
            ALOGI(
                "AMEDIACODEC__INFO_OUTPUT_FORMAT_CHANGED\n"
                "    width-height: (%d x %d)\n"
                "    color-format: (%s: 0x%x)\n"
                "    stride:       (%d)\n"
                "    slice-height: (%d)\n"
                "    crop:         (%d, %d, %d, %d)\n"
                ,
                width, height,
                SDL_AMediaCodec_getColorFormatName(color_format), color_format,
                stride,
                slice_height,
                crop_left, crop_top, crop_right, crop_bottom);
        }
        // continue;
    } else if (output_buffer_index == AMEDIACODEC__INFO_TRY_AGAIN_LATER) {
        AMCTRACE("AMEDIACODEC__INFO_TRY_AGAIN_LATER\n");
        // continue;
    } else if (output_buffer_index < 0) {
        // enqueue packet as a fake picture
        PacketQueue *fake_q = &opaque->fake_pictq;
        SDL_LockMutex(fake_q->mutex);
        if (!fake_q->abort_request && fake_q->nb_packets <= 0) {
            SDL_CondWaitTimeout(fake_q->cond, fake_q->mutex, 1000);
        }
        SDL_UnlockMutex(fake_q->mutex);

        if (fake_q->abort_request) {
            ret = -1;
            goto fail;
        } else {
            AVPacket pkt;
            int dequeue_ret = ffp_packet_queue_get(&opaque->fake_pictq, &pkt, 0, &opaque->fake_pictq_serial);
            if (dequeue_ret < 0) {
                ret = -1;
                goto fail;
            } else if (dequeue_ret > 0) {
                if (!ffp_is_flush_packet(&pkt)) {
                    if (dequeue_count)
                        ++*dequeue_count;

                    ret = amc_fill_frame_fake(node, frame, got_frame, &pkt);
                    av_free_packet(&pkt);
                }
                goto done;
            }
        }
    } else if (output_buffer_index >= 0) {
        ffp->vdps = SDL_SpeedSamplerAdd(&opaque->sampler, FFP_SHOW_VDPS_MEDIACODEC, "vdps[MediaCodec]");

        if (dequeue_count)
            ++*dequeue_count;

#ifdef FFP_SHOW_AMC_VDPS
        {
            if (opaque->benchmark_start_time == 0) {
                opaque->benchmark_start_time   = SDL_GetTickHR();
            }
            opaque->benchmark_frame_count += 1;
            if (0 == (opaque->benchmark_frame_count % 240)) {
                Uint64 diff = SDL_GetTickHR() - opaque->benchmark_start_time;
                double per_frame_ms = ((double) diff) / opaque->benchmark_frame_count;
                double fps          = ((double) opaque->benchmark_frame_count) * 1000 / diff;
                ALOGE("%lf fps, %lf ms/frame, %"PRIu64" frames\n",
                      fps, per_frame_ms, opaque->benchmark_frame_count);
            }
        }
#endif
#ifdef FFP_AMC_DISABLE_OUTPUT
        SDL_AMediaCodec_releaseOutputBuffer(opaque->acodec, output_buffer_index, false);
        goto done;
#endif

        if (opaque->n_buf_out) {
            AMC_Buf_Out *buf_out;

            if (opaque->off_buf_out < opaque->n_buf_out) {
                // ALOGD("filling buffer... %d", opaque->off_buf_out);
                buf_out = &opaque->amc_buf_out[opaque->off_buf_out++];
                buf_out->acodec_serial = SDL_AMediaCodec_getSerial(opaque->acodec);
                buf_out->port = output_buffer_index;
                buf_out->info = bufferInfo;
                buf_out->pts = pts_from_buffer_info(node, &bufferInfo);
                sort_amc_buf_out(opaque->amc_buf_out, opaque->off_buf_out);
            } else {
                double pts;

                pts = pts_from_buffer_info(node, &bufferInfo);
                if (opaque->last_queued_pts != AV_NOPTS_VALUE &&
                    pts < opaque->last_queued_pts) {
                    // FIXME: drop unordered picture to avoid dither
                    // ALOGE("early picture, drop!");
                    // SDL_AMediaCodec_releaseOutputBuffer(opaque->acodec, output_buffer_index, false);
                    // goto done;
                }
                /* already sorted */
                buf_out = &opaque->amc_buf_out[opaque->off_buf_out - 1];
                /* new picture is the most aged, send now */
                if (pts < buf_out->pts) {
                    ret = amc_fill_frame(node, frame, got_frame, output_buffer_index, SDL_AMediaCodec_getSerial(opaque->acodec), &bufferInfo);
                    opaque->last_queued_pts = pts;
                    // ALOGD("pts = %f", pts);
                } else {
                    int i;

                    /* find one to send */
                    for (i = opaque->off_buf_out - 1; i >= 0; i--) {
                        buf_out = &opaque->amc_buf_out[i];
                        if (pts > buf_out->pts) {
                            ret = amc_fill_frame(node, frame, got_frame, buf_out->port, buf_out->acodec_serial, &buf_out->info);
                            opaque->last_queued_pts = buf_out->pts;
                            // ALOGD("pts = %f", buf_out->pts);
                            /* replace for sort later */
                            buf_out->acodec_serial = SDL_AMediaCodec_getSerial(opaque->acodec);
                            buf_out->port = output_buffer_index;
                            buf_out->info = bufferInfo;
                            buf_out->pts = pts_from_buffer_info(node, &bufferInfo);
                            sort_amc_buf_out(opaque->amc_buf_out, opaque->n_buf_out);
                            break;
                        }
                    }
                    /* need to discard current buffer */
                    if (i < 0) {
                        // ALOGE("buffer too small, drop picture!");
                        SDL_AMediaCodec_releaseOutputBuffer(opaque->acodec, output_buffer_index, false);
                        goto done;
                    }
                }
            }
        } else {
            ret = amc_fill_frame(node, frame, got_frame, output_buffer_index, SDL_AMediaCodec_getSerial(opaque->acodec), &bufferInfo);
        }
    }

done:
    ret = 0;
fail:
    return ret;
}

static int drain_output_buffer(JNIEnv *env, IJKFF_Pipenode *node, int64_t timeUs, int *dequeue_count, AVFrame *frame, int *got_frame)
{
    IJKFF_Pipenode_Opaque *opaque = node->opaque;
    SDL_LockMutex(opaque->acodec_mutex);

    if (opaque->acodec_flush_request || opaque->acodec_reconfigure_request) {
        // TODO: invalid picture here?
        // let feed_input_buffer() get mutex
        SDL_CondWaitTimeout(opaque->acodec_cond, opaque->acodec_mutex, 100);
    }

    int ret = drain_output_buffer_l(env, node, timeUs, dequeue_count, frame, got_frame);
    SDL_UnlockMutex(opaque->acodec_mutex);
    return ret;
}

static void func_destroy(IJKFF_Pipenode *node)
{
    if (!node || !node->opaque)
        return;

    IJKFF_Pipenode_Opaque *opaque = node->opaque;

    SDL_DestroyCondP(&opaque->acodec_cond);
    SDL_DestroyMutexP(&opaque->acodec_mutex);
    SDL_DestroyCondP(&opaque->acodec_first_dequeue_output_cond);
    SDL_DestroyMutexP(&opaque->acodec_first_dequeue_output_mutex);

    SDL_AMediaCodec_decreaseReferenceP(&opaque->acodec);
    SDL_AMediaFormat_deleteP(&opaque->input_aformat);
    SDL_AMediaFormat_deleteP(&opaque->output_aformat);

#if AMC_USE_AVBITSTREAM_FILTER
    av_freep(&opaque->orig_extradata);
#endif

    ffp_packet_queue_destroy(&opaque->fake_pictq);

    if (opaque->bsfc) {
        av_bitstream_filter_close(opaque->bsfc);
        opaque->bsfc = NULL;
    }

    JNIEnv *env = NULL;
    if (JNI_OK == SDL_JNI_SetupThreadEnv(&env)) {
        if (opaque->jsurface != NULL) {
            SDL_JNI_DeleteGlobalRefP(env, &opaque->jsurface);
        }
    }
}

static int func_run_sync(IJKFF_Pipenode *node)
{
    JNIEnv                *env      = NULL;
    IJKFF_Pipenode_Opaque *opaque   = node->opaque;
    FFPlayer              *ffp      = opaque->ffp;
    VideoState            *is       = ffp->is;
    Decoder               *d        = &is->viddec;
    PacketQueue           *q        = d->queue;
    int                    ret      = 0;
    int                    dequeue_count = 0;
    AVFrame               *frame    = NULL;
    int                    got_frame = 0;
    AVRational             tb         = is->video_st->time_base;
    AVRational             frame_rate = av_guess_frame_rate(is->ic, is->video_st, NULL);
    double                 duration;
    double                 pts;

    if (!opaque->acodec) {
        return ffp_video_thread(ffp);
    }

    if (JNI_OK != SDL_JNI_SetupThreadEnv(&env)) {
        ALOGE("%s: SetupThreadEnv failed\n", __func__);
        return -1;
    }

    frame = av_frame_alloc();
    if (!frame)
        goto fail;

    if (opaque->frame_rotate_degrees == 90 || opaque->frame_rotate_degrees == 270) {
        opaque->frame_width  = opaque->avctx->height;
        opaque->frame_height = opaque->avctx->width;
    } else {
        opaque->frame_width  = opaque->avctx->width;
        opaque->frame_height = opaque->avctx->height;
    }

    opaque->enqueue_thread = SDL_CreateThreadEx(&opaque->_enqueue_thread, enqueue_thread_func, node, "amediacodec_input_thread");
    if (!opaque->enqueue_thread) {
        ALOGE("%s: SDL_CreateThreadEx failed\n", __func__);
        ret = -1;
        goto fail;
    }

    while (!q->abort_request) {
        int64_t timeUs = opaque->acodec_first_dequeue_output_request ? 0 : AMC_OUTPUT_TIMEOUT_US;
        got_frame = 0;
        ret = drain_output_buffer(env, node, timeUs, &dequeue_count, frame, &got_frame);
        if (opaque->acodec_first_dequeue_output_request) {
            SDL_LockMutex(opaque->acodec_first_dequeue_output_mutex);
            opaque->acodec_first_dequeue_output_request = false;
            SDL_CondSignal(opaque->acodec_first_dequeue_output_cond);
            SDL_UnlockMutex(opaque->acodec_first_dequeue_output_mutex);
        }
        if (ret != 0) {
            ret = -1;
            if (got_frame && frame->opaque)
                SDL_VoutAndroid_releaseBufferProxyP(opaque->weak_vout, (SDL_AMediaCodecBufferProxy **)&frame->opaque, false);
            goto fail;
        }
        if (got_frame) {
            duration = (frame_rate.num && frame_rate.den ? av_q2d((AVRational){frame_rate.den, frame_rate.num}) : 0);
            pts = (frame->pts == AV_NOPTS_VALUE) ? NAN : frame->pts * av_q2d(tb);
            ret = ffp_queue_picture(ffp, frame, pts, duration, av_frame_get_pkt_pos(frame), is->viddec.pkt_serial);
            if (ret) {
                if (frame->opaque)
                    SDL_VoutAndroid_releaseBufferProxyP(opaque->weak_vout, (SDL_AMediaCodecBufferProxy **)&frame->opaque, false);
            }
            av_frame_unref(frame);
        }
    }

fail:
    av_frame_free(&frame);
    ffp_packet_queue_abort(&opaque->fake_pictq);
    if (opaque->n_buf_out) {
        free(opaque->amc_buf_out);
        opaque->n_buf_out = 0;
        opaque->amc_buf_out = NULL;
        opaque->off_buf_out = 0;
        opaque->last_queued_pts = AV_NOPTS_VALUE;
    }
    if (opaque->acodec) {
        SDL_VoutAndroid_invalidateAllBuffers(opaque->weak_vout);
        SDL_AMediaCodec_stop(opaque->acodec);
    }
    SDL_WaitThread(opaque->enqueue_thread, NULL);
    SDL_AMediaCodec_decreaseReferenceP(&opaque->acodec);
    ALOGI("MediaCodec: %s: exit: %d", __func__, ret);
    return ret;
#if 0
fallback_to_ffplay:
    ALOGW("fallback to ffplay decoder\n");
    return ffp_video_thread(opaque->ffp);
#endif
}

static int func_flush(IJKFF_Pipenode *node)
{
    IJKFF_Pipenode_Opaque *opaque   = node->opaque;

    if (!opaque)
        return -1;

    opaque->acodec_flush_request = true;

    return 0;
}

IJKFF_Pipenode *ffpipenode_create_video_decoder_from_android_mediacodec(FFPlayer *ffp, IJKFF_Pipeline *pipeline, SDL_Vout *vout)
{
    ALOGD("ffpipenode_create_video_decoder_from_android_mediacodec()\n");
    if (SDL_Android_GetApiLevel() < IJK_API_16_JELLY_BEAN)
        return NULL;

    if (!ffp || !ffp->is)
        return NULL;

    IJKFF_Pipenode *node = ffpipenode_alloc(sizeof(IJKFF_Pipenode_Opaque));
    if (!node)
        return node;

    VideoState            *is     = ffp->is;
    IJKFF_Pipenode_Opaque *opaque = node->opaque;
    JNIEnv                *env    = NULL;
    int                    ret    = 0;
    int                    rotate_degrees = 0;
    jobject                jsurface = NULL;

    node->func_destroy  = func_destroy;
    node->func_run_sync = func_run_sync;
    node->func_flush    = func_flush;
    opaque->pipeline    = pipeline;
    opaque->ffp         = ffp;
    opaque->decoder     = &is->viddec;
    opaque->weak_vout   = vout;

    opaque->avctx = opaque->decoder->avctx;
    switch (opaque->avctx->codec_id) {
    case AV_CODEC_ID_H264:
        if (!ffp->mediacodec_avc && !ffp->mediacodec_all_videos) {
            ALOGE("%s: MediaCodec: AVC/H264 is disabled. codec_id:%d \n", __func__, opaque->avctx->codec_id);
            goto fail;
        }
        switch (opaque->avctx->profile) {
            case FF_PROFILE_H264_BASELINE:
                ALOGI("%s: MediaCodec: H264_BASELINE: enabled\n", __func__);
                break;
            case FF_PROFILE_H264_CONSTRAINED_BASELINE:
                ALOGI("%s: MediaCodec: H264_CONSTRAINED_BASELINE: enabled\n", __func__);
                break;
            case FF_PROFILE_H264_MAIN:
                ALOGI("%s: MediaCodec: H264_MAIN: enabled\n", __func__);
                break;
            case FF_PROFILE_H264_EXTENDED:
                ALOGI("%s: MediaCodec: H264_EXTENDED: enabled\n", __func__);
                break;
            case FF_PROFILE_H264_HIGH:
                ALOGI("%s: MediaCodec: H264_HIGH: enabled\n", __func__);
                break;
            case FF_PROFILE_H264_HIGH_10:
                ALOGW("%s: MediaCodec: H264_HIGH_10: disabled\n", __func__);
                goto fail;
            case FF_PROFILE_H264_HIGH_10_INTRA:
                ALOGW("%s: MediaCodec: H264_HIGH_10_INTRA: disabled\n", __func__);
                goto fail;
            case FF_PROFILE_H264_HIGH_422:
                ALOGW("%s: MediaCodec: H264_HIGH_10_422: disabled\n", __func__);
                goto fail;
            case FF_PROFILE_H264_HIGH_422_INTRA:
                ALOGW("%s: MediaCodec: H264_HIGH_10_INTRA: disabled\n", __func__);
                goto fail;
            case FF_PROFILE_H264_HIGH_444:
                ALOGW("%s: MediaCodec: H264_HIGH_10_444: disabled\n", __func__);
                goto fail;
            case FF_PROFILE_H264_HIGH_444_PREDICTIVE:
                ALOGW("%s: MediaCodec: H264_HIGH_444_PREDICTIVE: disabled\n", __func__);
                goto fail;
            case FF_PROFILE_H264_HIGH_444_INTRA:
                ALOGW("%s: MediaCodec: H264_HIGH_444_INTRA: disabled\n", __func__);
                goto fail;
            case FF_PROFILE_H264_CAVLC_444:
                ALOGW("%s: MediaCodec: H264_CAVLC_444: disabled\n", __func__);
                goto fail;
            default:
                ALOGW("%s: MediaCodec: (%d) unknown profile: disabled\n", __func__, opaque->avctx->profile);
                goto fail;
        }
        strcpy(opaque->mcc.mime_type, SDL_AMIME_VIDEO_AVC);
        opaque->mcc.profile = opaque->avctx->profile;
        opaque->mcc.level   = opaque->avctx->level;
        break;
    case AV_CODEC_ID_HEVC:
        if (!ffp->mediacodec_hevc && !ffp->mediacodec_all_videos) {
            ALOGE("%s: MediaCodec/HEVC is disabled. codec_id:%d \n", __func__, opaque->avctx->codec_id);
            goto fail;
        }
        strcpy(opaque->mcc.mime_type, SDL_AMIME_VIDEO_HEVC);
        opaque->mcc.profile = opaque->avctx->profile;
        opaque->mcc.level   = opaque->avctx->level;
        break;
    default:
        ALOGE("%s:create: not H264 or H265/HEVC, codec_id:%d \n", __func__, opaque->avctx->codec_id);
        goto fail;
    }

    if (JNI_OK != SDL_JNI_SetupThreadEnv(&env)) {
        ALOGE("%s:create: SetupThreadEnv failed\n", __func__);
        goto fail;
    }

    opaque->acodec_mutex                      = SDL_CreateMutex();
    opaque->acodec_cond                       = SDL_CreateCond();
    opaque->acodec_first_dequeue_output_mutex = SDL_CreateMutex();
    opaque->acodec_first_dequeue_output_cond  = SDL_CreateCond();

    ffp_packet_queue_init(&opaque->fake_pictq);
    ffp_packet_queue_start(&opaque->fake_pictq);

    if (!opaque->acodec_cond || !opaque->acodec_cond || !opaque->acodec_first_dequeue_output_mutex || !opaque->acodec_first_dequeue_output_cond) {
        ALOGE("%s:open_video_decoder: SDL_CreateCond() failed\n", __func__);
        goto fail;
    }

    ALOGI("AMediaFormat: %s, %dx%d\n", opaque->mcc.mime_type, opaque->avctx->width, opaque->avctx->height);
    opaque->input_aformat = SDL_AMediaFormatJava_createVideoFormat(env, opaque->mcc.mime_type, opaque->avctx->width, opaque->avctx->height);
    if (opaque->avctx->extradata && opaque->avctx->extradata_size > 0) {
        if ((opaque->avctx->codec_id == AV_CODEC_ID_H264 || opaque->avctx->codec_id == AV_CODEC_ID_HEVC)
            && opaque->avctx->extradata[0] == 1) {
#if AMC_USE_AVBITSTREAM_FILTER
            if (opaque->avctx->codec_id == AV_CODEC_ID_H264) {
                opaque->bsfc = av_bitstream_filter_init("h264_mp4toannexb");
                if (!opaque->bsfc) {
                    ALOGE("Cannot open the h264_mp4toannexb BSF!\n");
                    goto fail;
                }
            } else {
                opaque->bsfc = av_bitstream_filter_init("hevc_mp4toannexb");
                if (!opaque->bsfc) {
                    ALOGE("Cannot open the hevc_mp4toannexb BSF!\n");
                    goto fail;
                }
            }

            opaque->orig_extradata_size = opaque->avctx->extradata_size;
            opaque->orig_extradata = (uint8_t*) av_mallocz(opaque->avctx->extradata_size +
                                                      FF_INPUT_BUFFER_PADDING_SIZE);
            if (!opaque->orig_extradata) {
                goto fail;
            }
            memcpy(opaque->orig_extradata, opaque->avctx->extradata, opaque->avctx->extradata_size);
            for(int i = 0; i < opaque->avctx->extradata_size; i+=4) {
                ALOGE("csd-0[%d]: %02x%02x%02x%02x\n", opaque->avctx->extradata_size, (int)opaque->avctx->extradata[i+0], (int)opaque->avctx->extradata[i+1], (int)opaque->avctx->extradata[i+2], (int)opaque->avctx->extradata[i+3]);
            }
            SDL_AMediaFormat_setBuffer(opaque->input_aformat, "csd-0", opaque->avctx->extradata, opaque->avctx->extradata_size);
#else
            size_t   sps_pps_size   = 0;
            size_t   convert_size   = opaque->avctx->extradata_size + 20;
            uint8_t *convert_buffer = (uint8_t *)calloc(1, convert_size);
            if (!convert_buffer) {
                ALOGE("%s:sps_pps_buffer: alloc failed\n", __func__);
                goto fail;
            }
            if (opaque->avctx->codec_id == AV_CODEC_ID_H264) {
                if (0 != convert_sps_pps(opaque->avctx->extradata, opaque->avctx->extradata_size,
                                         convert_buffer, convert_size,
                                         &sps_pps_size, &opaque->nal_size)) {
                    ALOGE("%s:convert_sps_pps: failed\n", __func__);
                    goto fail;
                }
            } else {
                if (0 != convert_hevc_nal_units(opaque->avctx->extradata, opaque->avctx->extradata_size,
                                         convert_buffer, convert_size,
                                         &sps_pps_size, &opaque->nal_size)) {
                    ALOGE("%s:convert_hevc_nal_units: failed\n", __func__);
                    goto fail;
                }
            }
            SDL_AMediaFormat_setBuffer(opaque->input_aformat, "csd-0", convert_buffer, sps_pps_size);
            for(int i = 0; i < sps_pps_size; i+=4) {
                ALOGE("csd-0[%d]: %02x%02x%02x%02x\n", (int)sps_pps_size, (int)convert_buffer[i+0], (int)convert_buffer[i+1], (int)convert_buffer[i+2], (int)convert_buffer[i+3]);
            }
            free(convert_buffer);
#endif
        } else {
            // Codec specific data
            // SDL_AMediaFormat_setBuffer(opaque->aformat, "csd-0", opaque->avctx->extradata, opaque->avctx->extradata_size);
            ALOGE("csd-0: naked\n");
        }
    } else {
        ALOGE("no buffer(%d)\n", opaque->avctx->extradata_size);
    }

    rotate_degrees = ffp_get_video_rotate_degrees(ffp);
    if (ffp->mediacodec_auto_rotate &&
        rotate_degrees != 0 &&
        SDL_Android_GetApiLevel() >= IJK_API_21_LOLLIPOP) {
        ALOGI("amc: rotate in decoder: %d\n", rotate_degrees);
        opaque->frame_rotate_degrees = rotate_degrees;
        SDL_AMediaFormat_setInt32(opaque->input_aformat, "rotation-degrees", rotate_degrees);
        ffp_notify_msg2(ffp, FFP_MSG_VIDEO_ROTATION_CHANGED, 0);
    } else {
        ALOGI("amc: rotate notify: %d\n", rotate_degrees);
        ffp_notify_msg2(ffp, FFP_MSG_VIDEO_ROTATION_CHANGED, rotate_degrees);
    }

    if (!ffpipeline_select_mediacodec_l(pipeline, &opaque->mcc) || !opaque->mcc.codec_name[0]) {
        ALOGE("amc: no suitable codec\n");
        goto fail;
    }

    jsurface = ffpipeline_get_surface_as_global_ref(env, pipeline);
    ret = reconfigure_codec_l(env, node, jsurface);
    JJK_DeleteGlobalRef__p(env, &jsurface);
    if (ret != 0)
        goto fail;

    ffp_set_video_codec_info(ffp, MEDIACODEC_MODULE_NAME, opaque->mcc.codec_name);

    opaque->off_buf_out = 0;
    if (opaque->n_buf_out) {
        int i;

        opaque->amc_buf_out = calloc(opaque->n_buf_out, sizeof(*opaque->amc_buf_out));
        assert(opaque->amc_buf_out != NULL);
        for (i = 0; i < opaque->n_buf_out; i++)
            opaque->amc_buf_out[i].pts = AV_NOPTS_VALUE;
    }

    SDL_SpeedSamplerReset(&opaque->sampler);
    return node;
fail:
    ffpipenode_free_p(&node);
    return NULL;
}
