#define MyAppName "ZERAVRIX"
#define MyAppVersion "0.9.0"
#define MyAppPublisher "ZERAVRIX Studio"
#define MyAppExeName "ZERAVRIX.exe"

[Setup]
AppId={{B6A3B2D8-7C1D-4B37-9A5D-9D6E8A7A5C10}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
DefaultDirName={autopf}\ZERAVRIX
DefaultGroupName=ZERAVRIX
OutputDir=..\desktop-app\build\installer
OutputBaseFilename=ZERAVRIX-Setup-{#MyAppVersion}
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

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Additional shortcuts:"

[Files]
Source: "..\desktop-app\build\compose\binaries\main\app\ZERAVRIX\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "logo.ico"; DestDir: "{app}"; Flags: ignoreversion

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
  TrayPage: TInputOptionWizardPage;

procedure InitializeWizard;
begin
  ThemePage := CreateInputOptionPage(wpSelectDir,
    'Choose your theme', 'Select the initial appearance',
    'You can change this later in Settings.', True, False);
  ThemePage.Add('Black and blue dark theme');
  ThemePage.Add('White and blue light theme');
  ThemePage.SelectedValueIndex := 0;

  LanguagePage := CreateInputOptionPage(ThemePage.ID,
    'Choose your language', 'Select the initial interface language',
    'English, Arabic, and French are available in Settings.', False, False);
  LanguagePage.Add('English');
  LanguagePage.Add('Arabic');
  LanguagePage.Add('French');
  LanguagePage.SelectedValueIndex := 0;

  NsfwPage := CreateInputOptionPage(LanguagePage.ID,
    'Content visibility', 'Choose whether NSFW source results are visible',
    'This setting controls visibility only and can be changed later in Settings.', True, False);
  NsfwPage.Add('Keep NSFW content hidden');
  NsfwPage.Add('Allow NSFW content from sources');
  NsfwPage.SelectedValueIndex := 0;

  TrayPage := CreateInputOptionPage(NsfwPage.ID,
    'Window close behavior', 'Choose what happens when you close the main window',
    'You can change this later in Settings.', True, False);
  TrayPage.Add('Keep ZERAVRIX running in the Windows system tray');
  TrayPage.Add('Fully exit ZERAVRIX when the window closes');
  TrayPage.SelectedValueIndex := 1;
end;

procedure CurStepChanged(CurStep: TSetupStep);
var
  SettingsDir, SettingsFile, Theme, Lang, Nsfw, Tray: String;
begin
  if CurStep = ssPostInstall then begin
    SettingsDir := AddBackslash(ExtractFileDir(ExpandConstant('{userappdata}'))) + '.zeravrix';
    ForceDirectories(SettingsDir);
    SettingsFile := SettingsDir + '\settings.properties';
    if ThemePage.SelectedValueIndex = 1 then Theme := 'LIGHT' else Theme := 'DARK';
    if LanguagePage.SelectedValueIndex = 1 then Lang := 'ARABIC'
    else if LanguagePage.SelectedValueIndex = 2 then Lang := 'FRENCH'
    else Lang := 'ENGLISH';
    if NsfwPage.SelectedValueIndex = 1 then Nsfw := 'true' else Nsfw := 'false';
    if TrayPage.SelectedValueIndex = 0 then Tray := 'true' else Tray := 'false';
    if not FileExists(SettingsFile) then
      SaveStringToFile(SettingsFile,
        '# ZERAVRIX first-run settings' + #13#10 +
        'theme=' + Theme + #13#10 +
        'language=' + Lang + #13#10 +
        'readerMode=AUTO' + #13#10 +
        'nsfwEnabled=' + Nsfw + #13#10 +
        'minimizeToTray=' + Tray + #13#10 +
        'refreshOnOpen=true' + #13#10 +
        'refreshIntervalHours=12' + #13#10, False);
  end;
end;
