#define MyAppName "ZERAVRIX"
#define MyAppVersion "1.4.0"
#define MyAppPublisher "U1M"
#define MyAppExeName "ZERAVRIX.exe"
[Setup]
AppId={{B6A3B2D8-7C1D-4B37-9A5D-9D6E8A7A5C10}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppCopyright=Copyright (c) 2026 U1M. All rights reserved.
LicenseFile=LICENSE.txt
DefaultDirName={localappdata}\Programs\ZERAVRIX
DefaultGroupName=ZERAVRIX
OutputDir=Z:\home\ubuntu\work_reader\deliverables\kotlin-reader-fix
OutputBaseFilename=ZERAVRIX-Setup-1.4.0
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
PrivilegesRequired=lowest
UninstallDisplayName=ZERAVRIX
SetupIconFile=logo.ico
WizardSmallImageFile=logo.bmp
WizardImageFile=logo.bmp
DisableProgramGroupPage=yes
DisableWelcomePage=yes
DisableDirPage=yes
[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"
[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Additional shortcuts:"
[Files]
Source: "..\..\deliverables\portable\ZERAVRIX\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "logo.ico"; DestDir: "{app}"; Flags: ignoreversion
Source: "LICENSE.txt"; DestDir: "{app}"; Flags: ignoreversion
[Icons]
Name: "{group}\ZERAVRIX"; Filename: "{app}\{#MyAppExeName}"; IconFilename: "{app}\logo.ico"
Name: "{autodesktop}\ZERAVRIX"; Filename: "{app}\{#MyAppExeName}"; IconFilename: "{app}\logo.ico"; Tasks: desktopicon
[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "Launch ZERAVRIX"; Flags: nowait postinstall skipifsilent
[Code]
var
  ThemePage: TInputOptionWizardPage;
  LanguagePage: TInputOptionWizardPage;
  NsfwPage: TInputOptionWizardPage;
procedure InitializeWizard;
begin
  ThemePage := CreateInputOptionPage(wpSelectDir, 'Appearance', 'Choose the starting visual style', 'Dark uses black and blue for a low-light desktop reader. Light uses white and blue for brighter rooms. Artwork themes can be selected later inside ZERAVRIX Settings.', True, False);
  ThemePage.Add('Dark — black and blue'); ThemePage.Add('Light — white and blue'); ThemePage.SelectedValueIndex := 0;
  LanguagePage := CreateInputOptionPage(ThemePage.ID, 'Interface language', 'Choose the initial language', 'This changes navigation, reader controls, settings labels, and reading-direction defaults. English, Arabic, and French remain changeable later in Settings.', False, False);
  LanguagePage.Add('English — left-to-right'); LanguagePage.Add('Arabic — right-to-left'); LanguagePage.Add('French — left-to-right'); LanguagePage.SelectedValueIndex := 0;
  NsfwPage := CreateInputOptionPage(LanguagePage.ID, 'Source content visibility', 'Choose whether adult-labelled sources are shown', 'When hidden, sources identified as hentai, adult, NSFW, or 18+ disappear from Explore, Sources, and search results. This does not alter third-party websites.', True, False);
  NsfwPage.Add('Hide adult-labelled sources'); NsfwPage.Add('Show adult-labelled sources'); NsfwPage.SelectedValueIndex := 0;
end;
procedure CurStepChanged(CurStep: TSetupStep);
var SettingsDir, SettingsFile, Theme, Lang, Nsfw: String;
begin
  if CurStep = ssPostInstall then begin
    SettingsDir := AddBackslash(ExtractFileDir(ExpandConstant('{userappdata}'))) + '.zeravrix';
    ForceDirectories(SettingsDir);
    SettingsFile := SettingsDir + '\settings.properties';
    if ThemePage.SelectedValueIndex = 1 then Theme := 'LIGHT' else Theme := 'DARK';
    if LanguagePage.SelectedValueIndex = 1 then Lang := 'ARABIC' else if LanguagePage.SelectedValueIndex = 2 then Lang := 'FRENCH' else Lang := 'ENGLISH';
    if NsfwPage.SelectedValueIndex = 1 then Nsfw := 'true' else Nsfw := 'false';
    if not FileExists(SettingsFile) then SaveStringToFile(SettingsFile, '# ZERAVRIX first-run settings' + #13#10 + '# Copyright (c) 2026 U1M. All rights reserved.' + #13#10 + 'theme=' + Theme + #13#10 + 'language=' + Lang + #13#10 + 'readerMode=AUTO' + #13#10 + 'nsfwEnabled=' + Nsfw + #13#10 + 'minimizeToTray=false' + #13#10 + 'animationLevel=SMOOTH' + #13#10 + 'refreshOnOpen=true' + #13#10 + 'refreshIntervalHours=12' + #13#10, False);
  end;
end;
