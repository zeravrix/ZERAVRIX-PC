#include "zeravrix/reader/WicImageDecoder.h"

#ifdef _WIN32

#include <Windows.h>
#include <wincodec.h>
#include <wrl/client.h>

#include <limits>
#include <stdexcept>

namespace zeravrix::reader {

std::shared_ptr<NativeImageData> WicImageDecoder::decode(std::filesystem::path const& path) const {
    using Microsoft::WRL::ComPtr;

    ComPtr<IWICImagingFactory> factory;
    if (FAILED(CoCreateInstance(
            CLSID_WICImagingFactory2,
            nullptr,
            CLSCTX_INPROC_SERVER,
            IID_PPV_ARGS(&factory)))) {
        throw std::runtime_error("Unable to initialize Windows Imaging Component");
    }

    ComPtr<IWICBitmapDecoder> decoder;
    if (FAILED(factory->CreateDecoderFromFilename(
            path.c_str(),
            nullptr,
            GENERIC_READ,
            WICDecodeMetadataCacheOnDemand,
            &decoder))) {
        throw std::runtime_error("Unable to decode image file");
    }

    ComPtr<IWICBitmapFrameDecode> frame;
    if (FAILED(decoder->GetFrame(0, &frame))) throw std::runtime_error("Image contains no frames");

    UINT width = 0;
    UINT height = 0;
    if (FAILED(frame->GetSize(&width, &height)) || width == 0 || height == 0) {
        throw std::runtime_error("Image has invalid dimensions");
    }
    constexpr UINT channels = 4;
    if (height > std::numeric_limits<UINT>::max() / (width * channels)) {
        throw std::runtime_error("Image dimensions exceed safe decode limits");
    }
    const UINT stride = width * channels;
    const UINT buffer_size = stride * height;

    ComPtr<IWICFormatConverter> converter;
    if (FAILED(factory->CreateFormatConverter(&converter)) ||
        FAILED(converter->Initialize(
            frame.Get(),
            GUID_WICPixelFormat32bppPBGRA,
            WICBitmapDitherTypeNone,
            nullptr,
            0.0,
            WICBitmapPaletteTypeCustom))) {
        throw std::runtime_error("Unable to convert image pixel format");
    }

    auto image = std::make_shared<NativeImageData>();
    image->width = width;
    image->height = height;
    image->stride = stride;
    image->pixels.resize(buffer_size);
    if (FAILED(converter->CopyPixels(nullptr, stride, buffer_size,
                                     reinterpret_cast<BYTE*>(image->pixels.data())))) {
        throw std::runtime_error("Unable to copy decoded image pixels");
    }
    return image;
}

} // namespace zeravrix::reader

#endif
