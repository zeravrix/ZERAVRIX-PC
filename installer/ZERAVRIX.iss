#define MyAppName "ZERAVRIX"
#define MyAppVersion "1.7.0"
#define MyAppPublisher "ZERAVRIX"
#define MyAppURL "https://zeravrix.pages.dev"
#define MyAppExeName "ZERAVRIX.exe"

[Setup]
AppId={{B6A3B2D8-7C1D-4B37-9A5D-9D6E8A7A5C10}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}
AppUpdatesURL={#MyAppURL}
DefaultDirName={autopf}\ZERAVRIX
DefaultGroupName=ZERAVRIX
OutputDir=..\..\release
OutputBaseFilename=ZERAVRIX-{#MyAppVersion}-Setup
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern
WizardResizable=no
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
PrivilegesRequired=lowest
UninstallDisplayName={#MyAppName}
UninstallDisplayIcon={app}\logo.ico
SetupIconFile=logo.ico
WizardSmallImageFile=logo.bmp
WizardImageFile=logo.bmp
DisableProgramGroupPage=yes
VersionInfoVersion=1.7.0.0
VersionInfoCompany={#MyAppPublisher}
VersionInfoDescription={#MyAppName} Setup
VersionInfoProductName={#MyAppName}

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Additional shortcuts:"

[Files]
Source: "..\desktop-app\build\compose\binaries\main\app\ZERAVRIX\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "logo.ico"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{group}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; IconFilename: "{app}\logo.ico"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; IconFilename: "{app}\logo.ico"; Tasks: desktopicon

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "Launch {#MyAppName}"; Flags: nowait postinstall skipifsilent

[Code]
var
  ThemePage: TInputOptionWizardPage;
  LanguagePage: TInputOptionWizardPage;
  NsfwPage: TInputOptionWizardPage;

procedure InitializeWizard;
begin
  ThemePage := CreateInputOptionPage(wpSelectDir,
    'Visual Appearance', 'Select your preferred reading theme',
    'ZERAVRIX includes 13 curated themes. You can change this anytime in Settings.', True, False);
  ThemePage.Add('Dark Theme (Totoro Indigo)');
  ThemePage.Add('Light Theme (Pure Clean)');
  ThemePage.Add('AMOLED Void (Pure #000000 Black)');
  ThemePage.SelectedValueIndex := 0;

  LanguagePage := CreateInputOptionPage(ThemePage.ID,
    'Interface Language', 'Select your initial application language',
    'You can also customize individual source languages in Settings.', False, False);
  LanguagePage.Add('English');
  LanguagePage.Add('Arabic');
  LanguagePage.Add('French');
  LanguagePage.SelectedValueIndex := 0;

  NsfwPage := CreateInputOptionPage(LanguagePage.ID,
    'Content Filter', 'Choose whether mature 18+ content is displayed',
    'This can be toggled on or off at any time in Settings.', True, False);
  NsfwPage.Add('Keep mature 18+ content hidden');
  NsfwPage.Add('Allow mature 18+ content');
  NsfwPage.SelectedValueIndex := 0;
end;

procedure CurStepChanged(CurStep: TSetupStep);
var
  SettingsDir, SettingsFile, Theme, Lang, Nsfw, Amoled: String;
begin
  if CurStep = ssPostInstall then begin
    SettingsDir := AddBackslash(ExtractFileDir(ExpandConstant('{userappdata}'))) + '.zeravrix';
    ForceDirectories(SettingsDir);
    SettingsFile := SettingsDir + '\settings.properties';
    
    if ThemePage.SelectedValueIndex = 1 then begin
      Theme := 'LIGHT';
      Amoled := 'false';
    end else if ThemePage.SelectedValueIndex = 2 then begin
      Theme := 'DARK';
      Amoled := 'true';
    end else begin
      Theme := 'DARK';
      Amoled := 'false';
    end;

    if LanguagePage.SelectedValueIndex = 1 then Lang := 'ARABIC'
    else if LanguagePage.SelectedValueIndex = 2 then Lang := 'FRENCH'
    else Lang := 'ENGLISH';

    if NsfwPage.SelectedValueIndex = 1 then Nsfw := 'true' else Nsfw := 'false';

    if not FileExists(SettingsFile) then
      SaveStringToFile(SettingsFile,
        '# ZERAVRIX first-run settings' + #13#10 +
        'theme=' + Theme + #13#10 +
        'isAmoled=' + Amoled + #13#10 +
        'language=' + Lang + #13#10 +
        'readerMode=VERTICAL' + #13#10 +
        'nsfwEnabled=' + Nsfw + #13#10 +
        'minimizeToTray=false' + #13#10 +
        'refreshOnOpen=true' + #13#10 +
        'refreshIntervalHours=12' + #13#10, False);
  end;
end;
