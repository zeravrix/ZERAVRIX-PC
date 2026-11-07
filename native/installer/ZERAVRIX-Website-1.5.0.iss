#define MyAppName "ZERAVRIX Website Edition"
#define MyAppVersion "1.5.0"
#define MyAppPublisher "U1M"
#define MyAppExeName "ZERAVRIX.exe"

[Setup]
AppId={{B6A3B2D8-7C1D-4B37-9A5D-9D6E8A7A5C10}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL=https://github.com/
AppCopyright=Copyright (c) 2026 U1M
DefaultDirName={localappdata}\Programs\ZERAVRIX Website Edition
DefaultGroupName=ZERAVRIX Website Edition
OutputDir=..\build\installer
OutputBaseFilename=ZERAVRIX-Website-Setup-{#MyAppVersion}
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
PrivilegesRequired=lowest
UninstallDisplayName={#MyAppName}
DisableProgramGroupPage=yes

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Additional shortcuts:"

[Files]
Source: "..\build\ZERAVRIX.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "..\build\app\*"; DestDir: "{app}\app"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "..\build\runtime\*"; DestDir: "{app}\runtime"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "..\build\resources\*"; DestDir: "{app}\resources"; Flags: ignoreversion recursesubdirs createallsubdirs skipifsourcedoesntexist

[Icons]
Name: "{group}\ZERAVRIX Website Edition"; Filename: "{app}\{#MyAppExeName}"
Name: "{autodesktop}\ZERAVRIX Website Edition"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "Launch {#MyAppName}"; Flags: nowait postinstall skipifsilent

[Code]
var
  ThemePage: TInputOptionWizardPage;
  ArtworkPage: TInputOptionWizardPage;
  LanguagePage: TInputOptionWizardPage;
  NsfwPage: TInputOptionWizardPage;
  TrayPage: TInputOptionWizardPage;

procedure InitializeWizard;
begin
  ThemePage := CreateInputOptionPage(wpSelectDir,
    'Choose your base theme', 'Select the initial appearance',
    'You can change this later in Settings.', True, False);
  ThemePage.Add('Black and blue dark theme');
  ThemePage.Add('White and blue light theme');
  ThemePage.SelectedValueIndex := 0;

  ArtworkPage := CreateInputOptionPage(ThemePage.ID,
    'Choose artwork theme', 'Select an optional website-edition artwork theme',
    'Custom artwork themes are available in the website release and can be changed in Settings.', False, False);
  ArtworkPage.Add('Standard ZERAVRIX');
  ArtworkPage.Add('Spider-Verse');
  ArtworkPage.Add('Alucard');
  ArtworkPage.Add('Attack on Titan');
  ArtworkPage.SelectedValueIndex := 0;

  LanguagePage := CreateInputOptionPage(ArtworkPage.ID,
    'Choose your language', 'Select the initial interface language',
    'English, Arabic, and French are available in Settings.', False, False);
  LanguagePage.Add('English');
  LanguagePage.Add('Arabic');
  LanguagePage.Add('French');
  LanguagePage.SelectedValueIndex := 0;

  NsfwPage := CreateInputOptionPage(LanguagePage.ID,
    'Content visibility', 'Choose whether NSFW source results are visible',
    'This option is available only in the website edition and can be changed later in Settings.', True, False);
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
  SettingsDir, SettingsFile, Theme, Artwork, Lang, Nsfw, Tray: String;
begin
  if CurStep = ssPostInstall then begin
    SettingsDir := AddBackslash(ExpandConstant('{userappdata}')) + '.zeravrix-web';
    ForceDirectories(SettingsDir);
    SettingsFile := SettingsDir + '\settings.properties';
    if ThemePage.SelectedValueIndex = 1 then Theme := 'LIGHT' else Theme := 'DARK';
    case ArtworkPage.SelectedValueIndex of
      1: Artwork := 'SPIDER_VERSE';
      2: Artwork := 'ALUCARD';
      3: Artwork := 'ATTACK_ON_TITAN';
    else
      Artwork := 'NONE';
    end;
    if LanguagePage.SelectedValueIndex = 1 then Lang := 'ARABIC'
    else if LanguagePage.SelectedValueIndex = 2 then Lang := 'FRENCH'
    else Lang := 'ENGLISH';
    if NsfwPage.SelectedValueIndex = 1 then Nsfw := 'true' else Nsfw := 'false';
    if TrayPage.SelectedValueIndex = 0 then Tray := 'true' else Tray := 'false';
    if not FileExists(SettingsFile) then
      SaveStringToFile(SettingsFile,
        '# ZERAVRIX Website Edition first-run settings' + #13#10 +
        'theme=' + Theme + #13#10 +
        'artworkTheme=' + Artwork + #13#10 +
        'language=' + Lang + #13#10 +
        'readerMode=AUTO' + #13#10 +
        'nsfwEnabled=' + Nsfw + #13#10 +
        'minimizeToTray=' + Tray + #13#10 +
        'refreshOnOpen=true' + #13#10 +
        'refreshIntervalHours=12' + #13#10, False);
  end;
end;
