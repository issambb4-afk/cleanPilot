$ErrorActionPreference = 'Stop'

$packageArgs = @{
  packageName   = 'cleanpilot'
  unzipLocation = "$(Split-Path -parent $MyInvocation.MyCommand.Definition)"
  fileType      = 'msi'
  url64         = 'https://github.com/cleanpilot/cleanpilot/releases/download/v1.0.0/CleanPilot-1.0.0.msi'
  silentArgs    = '/qn /norestart'
  validExitCodes= @(0, 3010, 1641)
  checksum64    = '8043CB64D65F08DDA76FFDB093D41560319309C75FBA222502F9E72899EB58C6'
  checksumType64= 'sha256'
}

Install-ChocolateyPackage @packageArgs
