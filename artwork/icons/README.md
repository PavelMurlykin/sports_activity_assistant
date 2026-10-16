# Иконки приложения

Подключены 10 октября 2026 года по выбору пользователя.

- `sports-vortex.png`: выбранный вариант «Спортивный вихрь», без перегенерации композиции.
- `sports-reference.png`: исходное изображение пользователя с двумя видами спорта.
- `football.png`, `climbing.png`: отдельные изображения, подготовленные из образца встроенным `image_gen` с удалением общего фона; сохранены исходный стиль и элементы иконок.

## Android-ресурсы

Из каталога проекта выполните `./scripts/prepare-icon-resources.ps1` в PowerShell на Windows. Скрипт использует System.Drawing без сетевых зависимостей. Он экспортирует PNG в `app/src/main/res/drawable-nodpi`: значки спорта 256 × 256, слои иконки запуска 432 × 432.

Выбранная иконка уменьшена и центрирована внутри безопасной области 66 dp на слое 108 dp; сглажена только граница фонового квадрата. Отдельная контрастная маска используется для тематических иконок Android 13 и выше. Лаунчер применяет внешнюю форму; PNG спорта отображаются без тонировки и сохраняют пропорции. Требования: [Adaptive icons — Android Developers](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive).

## Промпты выделения видов спорта

Параметры обоих вызовов: исходный `sports-reference.png` как `referenced_image_paths`, `transparent_background: true`.

### Футбол

```text
Use case: background-extraction. Edit the supplied image, which contains two separate sport icons. Extract ONLY the complete FOOTBALL icon on the LEFT as one standalone transparent-background PNG. Keep the existing artwork exactly: the large black-and-white glossy football, white goal with green net behind it, green/lime/cyan swooshes and the small colored highlight stars. Preserve the original shapes, composition, panel placement, shading, colors, outline and dimensional illustration style; do not reinterpret or redesign. Remove all parts of the climbing icon on the right and remove the shared gray/green canvas and diffuse background haze. Retain the crisp colored edges of the football emblem, but remove the large atmospheric glow outside it. Center this one extracted football emblem on a square transparent canvas with only 4-6 percent transparent margin around its full silhouette. No background, no square tile, no new elements, no text, no duplicate icon, no cropping of the goal or ribbons.
```

### Скалолазание

```text
Use case: background-extraction. Edit the supplied image, which contains two separate sport icons. Extract ONLY the complete ROCK CLIMBING icon on the RIGHT as one standalone transparent-background PNG. Keep the existing artwork exactly: the orange-helmeted climber in dark clothing and orange backpack, original gripping pose, gray faceted climbing wall with orange and cyan holds, small gray mountain peaks behind and the compact orange shape above the climber. Preserve the original shapes, pose, composition, shading, colors, outline and dimensional illustration style; do not reinterpret or redesign. Remove every part of the football icon on the left and remove the shared gray canvas and diffuse background haze. Retain the crisp edges of the climbing emblem, but remove large atmospheric glow outside it. Center this one extracted climbing emblem on a square transparent canvas with only 4-6 percent transparent margin around its full silhouette. No background, no square tile, no new elements, no text, no duplicate icon, no cropping of the climber or wall.
```



