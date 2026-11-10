$ErrorActionPreference = "Stop"
$proj = Resolve-Path "$PSScriptRoot\.."
$docs = Join-Path $proj "docs"
$srcItem = Get-ChildItem -Path $docs -Filter "*.docx" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $srcItem) { Write-Error "No DOCX found under $docs"; exit 1 }
$src = $srcItem.FullName
$out = Join-Path $docs "_preview.pdf"
Write-Host "Source: $src"

$word = $null
try {
    $word = New-Object -ComObject Word.Application
    $word.Visible = $false
    $doc = $word.Documents.Open($src, $false, $true)
    $doc.ExportAsFixedFormat($out, 17)   # 17 = wdExportFormatPDF
    $doc.Close([Microsoft.Office.Interop.Word.WdSaveOptions]::wdDoNotSaveChanges)
    Write-Host "PDF exported: $out"
} finally {
    if ($word) { $word.Quit() }
}
