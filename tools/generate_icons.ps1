$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$res = 'D:\Projects\GitHub\KosenkovPro\balik.chekak.store\composeApp\src\androidMain\res'
$densities = [ordered]@{ 'mdpi' = 48; 'hdpi' = 72; 'xhdpi' = 96; 'xxhdpi' = 144; 'xxxhdpi' = 192 }

function P([double]$x, [double]$y) { [System.Drawing.PointF]::new([float]$x, [float]$y) }

function New-BalikIcon([int]$size, [bool]$round, [string]$path) {
    $bmp = [System.Drawing.Bitmap]::new($size, $size)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias

    $s = $size / 48.0

    # Фон: тёплый градиент (персиковый -> оранжевый)
    $rect = [System.Drawing.RectangleF]::new(0, 0, [float]$size, [float]$size)
    $c1 = [System.Drawing.Color]::FromArgb(255, 255, 196, 122)
    $c2 = [System.Drawing.Color]::FromArgb(255, 232, 106, 58)
    $brush = [System.Drawing.Drawing2D.LinearGradientBrush]::new($rect, $c1, $c2, [float]55.0)
    if ($round) {
        $gp = [System.Drawing.Drawing2D.GraphicsPath]::new()
        $gp.AddEllipse($rect)
        $g.FillPath($brush, $gp)
    } else {
        $g.FillRectangle($brush, $rect)
    }

    $ear = [System.Drawing.Color]::FromArgb(255, 62, 42, 38)
    $earBrush = [System.Drawing.SolidBrush]::new($ear)

    # Уши
    $g.FillPolygon($earBrush, [System.Drawing.PointF[]]@(
        (P (12 * $s) (20 * $s)), (P (15.5 * $s) (8.5 * $s)), (P (22 * $s) (15.5 * $s))))
    $g.FillPolygon($earBrush, [System.Drawing.PointF[]]@(
        (P (36 * $s) (20 * $s)), (P (32.5 * $s) (8.5 * $s)), (P (26 * $s) (15.5 * $s))))

    # Голова
    $g.FillEllipse($earBrush, [float](9 * $s), [float](12 * $s), [float](30 * $s), [float](28 * $s))

    # Глаза
    $eye = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 255, 248, 235))
    $g.FillEllipse($eye, [float](16.5 * $s), [float](22 * $s), [float](4.6 * $s), [float](5.6 * $s))
    $g.FillEllipse($eye, [float](26.9 * $s), [float](22 * $s), [float](4.6 * $s), [float](5.6 * $s))
    $pupil = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 38, 26, 24))
    $g.FillEllipse($pupil, [float](18.0 * $s), [float](24.4 * $s), [float](2.4 * $s), [float](2.9 * $s))
    $g.FillEllipse($pupil, [float](28.4 * $s), [float](24.4 * $s), [float](2.4 * $s), [float](2.9 * $s))

    # Нос
    $nose = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 255, 190, 175))
    $g.FillPolygon($nose, [System.Drawing.PointF[]]@(
        (P (22.6 * $s) (28.4 * $s)), (P (25.4 * $s) (28.4 * $s)), (P (24 * $s) (30.6 * $s))))

    # Улыбка
    $stroke = [System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(255, 255, 190, 175), [float](1.1 * $s))
    $g.DrawArc($stroke, [float](20.2 * $s), [float](29.6 * $s), [float](7.6 * $s), [float](5.0 * $s), [float]15, [float]150)

    # Усы
    $whisker = [System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(180, 62, 42, 38), [float](0.8 * $s))
    $g.DrawLine($whisker, [float](7.5 * $s), [float](26.5 * $s), [float](15.5 * $s), [float](28.2 * $s))
    $g.DrawLine($whisker, [float](7.5 * $s), [float](30.0 * $s), [float](15.5 * $s), [float](30.6 * $s))
    $g.DrawLine($whisker, [float](40.5 * $s), [float](26.5 * $s), [float](32.5 * $s), [float](28.2 * $s))
    $g.DrawLine($whisker, [float](40.5 * $s), [float](30.0 * $s), [float](32.5 * $s), [float](30.6 * $s))

    $g.Dispose()
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
}

foreach ($key in $densities.Keys) {
    $dir = Join-Path $res ("mipmap-" + $key)
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    $size = $densities[$key]
    New-BalikIcon $size $false (Join-Path $dir 'ic_launcher.png')
    New-BalikIcon $size $true (Join-Path $dir 'ic_launcher_round.png')
    "generated mipmap-$key ($size px)"
}
