#pragma once

#include <memory>

#include "zeravrix/app/JvmHostSupervisor.h"
#include <winrt/Microsoft.UI.Xaml.h>
#include <winrt/Microsoft.UI.Xaml.Controls.h>
#include <winrt/Microsoft.UI.Xaml.Navigation.h>

namespace zeravrix::app {

struct App final : winrt::Microsoft::UI::Xaml::ApplicationT<App> {
    App();
    void OnLaunched(winrt::Microsoft::UI::Xaml::LaunchActivatedEventArgs const& args);

private:
    winrt::Microsoft::UI::Xaml::Window window_{nullptr};
#ifdef _WIN32
    JvmHostSupervisor jvm_host_;
#endif
};

} // namespace zeravrix::app
