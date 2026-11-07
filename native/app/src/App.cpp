#include "zeravrix/app/App.h"

#include <array>
#include <cstdlib>
#include <filesystem>
#include <utility>

#ifdef _WIN32
#include <Windows.h>
#endif

#include <winrt/Windows.Foundation.h>
#include <winrt/Microsoft.UI.Xaml.Media.h>
#include <winrt/Microsoft.UI.Xaml.Controls.Primitives.h>

using namespace winrt;
using namespace Microsoft::UI::Xaml;
using namespace Microsoft::UI::Xaml::Controls;

namespace zeravrix::app {

App::App() {
    RequestedTheme(ElementTheme::Default);
}

void App::OnLaunched(LaunchActivatedEventArgs const&) {
#ifdef _WIN32
    wchar_t module_path[MAX_PATH]{};
    const auto module_length = GetModuleFileNameW(nullptr, module_path, MAX_PATH);
    if (module_length > 0) {
        const auto app_directory = std::filesystem::path(module_path, module_path + module_length).parent_path();
        auto host_executable = app_directory / L"ZERAVRIX-JVM-HOST.exe";
        if (!std::filesystem::is_regular_file(host_executable)) host_executable = app_directory / L"jvm-host.exe";
        const auto profile = std::getenv("USERPROFILE");
        const auto data_root = profile != nullptr
            ? std::filesystem::path(profile) / L".zeravrix-dev"
            : std::filesystem::temp_directory_path() / L".zeravrix-dev";
        jvm_host_.start(host_executable, data_root);
    }
#endif
    window_ = Window();

    auto navigation = NavigationView();
    navigation.IsBackButtonVisible(NavigationViewBackButtonVisible::Collapsed);
    navigation.IsSettingsVisible(false);
    navigation.PaneTitle(L"Zeravrix dev");
    navigation.HeaderContent(box_value(L"Native Windows reader — development build"));

    constexpr std::array<std::pair<LPCWSTR, LPCWSTR>, 7> pages{{
        {L"Library", L"Library"},
        {L"Search", L"Search"},
        {L"Downloads", L"Downloads"},
        {L"Reader", L"Reader"},
        {L"Sources", L"Sources"},
        {L"Music", L"Music"},
        {L"Settings", L"Settings"},
    }};
    for (auto const& item : pages) {
        auto nav_item = NavigationViewItem();
        nav_item.Content(box_value(item.first));
        nav_item.Tag(box_value(item.second));
        navigation.MenuItems().Append(nav_item);
    }

    auto content = StackPanel();
    content.Spacing(12);
    content.Padding(Thickness{32, 28, 32, 28});
    auto heading = TextBlock();
    heading.Text(L"Library");
    heading.FontSize(30);
    auto description = TextBlock();
    description.Text(L"The native shell is connected to the isolated JVM extension host through versioned local IPC.");
    description.TextWrapping(TextWrapping::Wrap);
    content.Children().Append(heading);
    content.Children().Append(description);

    navigation.Content(content);
    navigation.SelectedItem(navigation.MenuItems().GetAt(0));
    navigation.SelectionChanged([content, heading, description](auto const&, NavigationViewSelectionChangedEventArgs const& args) {
        auto selected = args.SelectedItem().try_as<NavigationViewItem>();
        if (!selected) return;
        auto tag = unbox_value<hstring>(selected.Tag());
        heading.Text(tag);
        description.Text(
            tag == L"Reader"
                ? L"Native reader surface: bounded image cache, virtualization, prefetch, zoom, and offline chapters."
                : tag == L"Sources"
                    ? L"Source management is delegated to ZERAVRIX-JVM-HOST.exe; this process never loads Java JARs."
                    : L"Native ZERAVRIX workspace. Feature migration proceeds milestone by milestone while existing data remains compatible.");
    });

    window_.Content(navigation);
    window_.Activate();
}

} // namespace zeravrix::app
