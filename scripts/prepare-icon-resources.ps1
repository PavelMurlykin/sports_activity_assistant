[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$sourceRoot = Join-Path $projectRoot 'artwork/icons'
$resourceRoot = Join-Path $projectRoot 'app/src/main/res/drawable-nodpi'
New-Item -ItemType Directory -Path $resourceRoot -Force | Out-Null
Add-Type -AssemblyName System.Drawing
$drawingReferences = @([System.Drawing.Bitmap].Assembly.Location, [System.Drawing.Color].Assembly.Location) | Select-Object -Unique
if ($PSVersionTable.PSEdition -eq 'Core') {
    $drawingReferences += 'System.Runtime'
    $drawingReferences += [System.Drawing.Bitmap].Assembly.GetReferencedAssemblies() |
        Where-Object { $_.Name -like 'System.Private.Windows.*' } | Select-Object -ExpandProperty Name
}
Add-Type -ReferencedAssemblies $drawingReferences -TypeDefinition @'
using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;

public static class SportsIconResources
{
    private static Bitmap Canvas(int side)
    {
        return new Bitmap(side, side, PixelFormat.Format32bppArgb);
    }

    private static Graphics Painter(Bitmap bitmap)
    {
        Graphics graphics = Graphics.FromImage(bitmap);
        graphics.Clear(Color.Transparent);
        graphics.InterpolationMode = InterpolationMode.HighQualityBicubic;
        graphics.PixelOffsetMode = PixelOffsetMode.HighQuality;
        graphics.CompositingQuality = CompositingQuality.HighQuality;
        return graphics;
    }

    public static void Sport(string source, string destination)
    {
        using (Bitmap original = new Bitmap(source))
        using (Bitmap result = Canvas(256))
        {
            int left = original.Width, top = original.Height, right = -1, bottom = -1;
            for (int y = 0; y < original.Height; y++)
            for (int x = 0; x < original.Width; x++)
            {
                if (original.GetPixel(x, y).A <= 8) continue;
                left = Math.Min(left, x); right = Math.Max(right, x);
                top = Math.Min(top, y); bottom = Math.Max(bottom, y);
            }
            if (right < left) throw new InvalidOperationException("Empty icon: " + source);
            Rectangle bounds = Rectangle.FromLTRB(left, top, right + 1, bottom + 1);
            float scale = 236f / Math.Max(bounds.Width, bounds.Height);
            int width = (int)Math.Round(bounds.Width * scale);
            int height = (int)Math.Round(bounds.Height * scale);
            using (Graphics graphics = Painter(result))
            {
                graphics.DrawImage(original, new Rectangle((256-width)/2, (256-height)/2, width, height),
                    bounds, GraphicsUnit.Pixel);
            }
            result.Save(destination, ImageFormat.Png);
        }
    }

    public static void Launcher(string source, string foreground, string monochrome)
    {
        // 108 dp at 4x density; the selected square artwork fits in the central 66 dp.
        using (Bitmap original = new Bitmap(source))
        using (Bitmap result = Canvas(432))
        using (Bitmap mono = Canvas(432))
        {
            const int side = 264;
            const int offset = 84;
            using (Bitmap reduced = Canvas(side))
            {
                using (Graphics graphics = Painter(reduced))
                    graphics.DrawImage(original, new Rectangle(0, 0, side, side));
                for (int y = 0; y < side; y++)
                for (int x = 0; x < side; x++)
                {
                    Color color = reduced.GetPixel(x, y);
                    // Fade only the empty outer background edge, avoiding a visible square seam.
                    int edge = Math.Min(Math.Min(x, y), Math.Min(side-1-x, side-1-y));
                    int alpha = color.A * Math.Min(edge, 8) / 8;
                    result.SetPixel(x+offset, y+offset, Color.FromArgb(alpha, color.R, color.G, color.B));
                    // A contrast mask lets Android tint the artwork for themed launcher icons.
                    int contrast = Math.Max(Math.Abs(color.R-37),
                        Math.Max(Math.Abs(color.G-48), Math.Abs(color.B-52)));
                    int mask = Math.Min(255, Math.Max(0, contrast-18) * 4);
                    mono.SetPixel(x+offset, y+offset, Color.FromArgb(mask*alpha/255, 255, 255, 255));
                }
            }
            result.Save(foreground, ImageFormat.Png);
            mono.Save(monochrome, ImageFormat.Png);
        }
    }
}
'@

[SportsIconResources]::Sport((Join-Path $sourceRoot 'football.png'), (Join-Path $resourceRoot 'sport_football.png'))
[SportsIconResources]::Sport((Join-Path $sourceRoot 'climbing.png'), (Join-Path $resourceRoot 'sport_climbing.png'))
[SportsIconResources]::Launcher((Join-Path $sourceRoot 'sports-vortex.png'),
    (Join-Path $resourceRoot 'ic_launcher_foreground.png'), (Join-Path $resourceRoot 'ic_launcher_monochrome.png'))
Get-ChildItem -LiteralPath $resourceRoot -Filter '*.png' | Select-Object Name, Length
