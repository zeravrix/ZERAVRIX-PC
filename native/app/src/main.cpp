#include "zeravrix/app/App.h"

#include <Windows.h>
#include <MddBootstrap.h>
#include <winrt/Microsoft.UI.Xaml.h>

int __stdcall wWinMain(HINSTANCE, HINSTANCE, PWSTR, int) {
    const auto bootstrap = MddBootstrapInitialize2(
        0x00010000,
        L"",
        MddBootstrapInitializeOptions::None);
    if (bootstrap != 0) return static_cast<int>(bootstrap);

    winrt::init_apartment(winrt::apartment_type::single_threaded);
    winrt::Microsoft::UI::Xaml::Application::Start([](auto&&) {
        winrt::make<zeravrix::app::App>();
    });
    MddBootstrapShutdown();
    return 0;
}
