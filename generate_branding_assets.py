import os
from PIL import Image, ImageDraw, ImageFilter

def create_svg_icon():
    svg = '''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="100%" height="100%">
  <defs>
    <!-- Background Gradient -->
    <linearGradient id="bgGrad" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#0F172A"/>
      <stop offset="50%" stop-color="#1E293B"/>
      <stop offset="100%" stop-color="#0A0F1D"/>
    </linearGradient>

    <!-- Border Glow Gradient -->
    <linearGradient id="borderGrad" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#38BDF8" stop-opacity="0.6"/>
      <stop offset="50%" stop-color="#818CF8" stop-opacity="0.3"/>
      <stop offset="100%" stop-color="#2563EB" stop-opacity="0.1"/>
    </linearGradient>

    <!-- Left Wing Gradient -->
    <linearGradient id="leftWing" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#38BDF8"/>
      <stop offset="40%" stop-color="#2563EB"/>
      <stop offset="100%" stop-color="#1D4ED8"/>
    </linearGradient>

    <!-- Right Wing Gradient -->
    <linearGradient id="rightWing" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#818CF8"/>
      <stop offset="50%" stop-color="#4F46E5"/>
      <stop offset="100%" stop-color="#3730A3"/>
    </linearGradient>

    <!-- Center Pilot Arrow / Spear Gradient -->
    <linearGradient id="centerSpear" x1="50%" y1="0%" x2="50%" y2="100%">
      <stop offset="0%" stop-color="#FFFFFF"/>
      <stop offset="35%" stop-color="#E0F2FE"/>
      <stop offset="70%" stop-color="#38BDF8"/>
      <stop offset="100%" stop-color="#0284C7"/>
    </linearGradient>

    <!-- Crystal Diamond / Pulse Core -->
    <linearGradient id="coreGlow" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#A5F3FC"/>
      <stop offset="50%" stop-color="#38BDF8"/>
      <stop offset="100%" stop-color="#0284C7"/>
    </linearGradient>

    <!-- Filter Glow -->
    <filter id="neonGlow" x="-20%" y="-20%" width="140%" height="140%">
      <feGaussianBlur stdDeviation="10" result="blur"/>
      <feMerge>
        <feMergeNode in="blur"/>
        <feMergeNode in="SourceGraphic"/>
      </feMerge>
    </filter>

    <filter id="softShadow" x="-10%" y="-10%" width="120%" height="120%">
      <feDropShadow dx="0" dy="12" stdDeviation="16" flood-color="#0284C7" flood-opacity="0.25"/>
    </filter>
  </defs>

  <!-- Base Squircle with Subtle Depth -->
  <rect x="24" y="24" width="464" height="464" rx="104" fill="url(#bgGrad)" stroke="url(#borderGrad)" stroke-width="3" filter="url(#softShadow)"/>

  <!-- Ambient Radiant Mesh Inside Squircle -->
  <circle cx="256" cy="220" r="140" fill="#2563EB" opacity="0.18" filter="blur(40px)"/>
  <circle cx="256" cy="310" r="90" fill="#38BDF8" opacity="0.12" filter="blur(30px)"/>

  <!-- Aerodynamic Shield & Pilot Chevron Wings -->
  <!-- Left Wing -->
  <path d="M256 108 
           L142 208 
           C134 215 130 225 130 236 
           L130 312 
           C130 326 142 336 156 332 
           L212 316 
           C218 314 224 316 228 320 
           L246 342 
           C251 348 255 352 256 354 
           L256 108 Z" 
        fill="url(#leftWing)" />

  <!-- Right Wing -->
  <path d="M256 108 
           L370 208 
           C378 215 382 225 382 236 
           L382 312 
           C382 326 370 336 356 332 
           L300 316 
           C294 314 288 316 284 320 
           L266 342 
           C261 348 257 352 256 354 
           L256 108 Z" 
        fill="url(#rightWing)" />

  <!-- High-Gloss Dynamic Facet Overlays -->
  <!-- Left Wing Highlight -->
  <path d="M256 108 L142 208 C140 210 148 206 168 196 L256 144 Z" fill="#FFFFFF" opacity="0.35"/>
  <!-- Right Wing Reflection -->
  <path d="M256 108 L370 208 C365 212 340 225 310 240 L256 160 Z" fill="#FFFFFF" opacity="0.15"/>

  <!-- Center Supersonic Pilot Arrow / High-Tech Navigator Spear -->
  <path d="M256 82 
           L290 190 
           C292 196 288 202 282 202 
           L266 202 
           L266 376 
           C266 384 256 388 256 388 
           C256 388 246 384 246 376 
           L246 202 
           L230 202 
           C224 202 220 196 222 190 
           L256 82 Z" 
        fill="url(#centerSpear)" 
        filter="url(#neonGlow)"/>

  <!-- Core Radiant Diamond / Clean Storage Pulse (Precision Node) -->
  <polygon points="256,196 280,230 256,264 232,230" fill="url(#coreGlow)" filter="url(#neonGlow)"/>
  <polygon points="256,206 272,230 256,254 240,230" fill="#FFFFFF" opacity="0.95"/>

  <!-- Horizontal Clean Telemetry Winglets (Precision Accents) -->
  <circle cx="178" cy="242" r="5" fill="#38BDF8" opacity="0.8"/>
  <circle cx="334" cy="242" r="5" fill="#818CF8" opacity="0.8"/>
  <line x1="168" y1="262" x2="198" y2="262" stroke="#38BDF8" stroke-width="2.5" stroke-linecap="round" opacity="0.5"/>
  <line x1="314" y1="262" x2="344" y2="262" stroke="#818CF8" stroke-width="2.5" stroke-linecap="round" opacity="0.5"/>
</svg>'''
    return svg

def create_svg_logo_horizontal(theme='dark'):
    fg_title = "#FFFFFF" if theme == 'dark' else "#0F172A"
    sub_title = "#94A3B8" if theme == 'dark' else "#475569"
    badge_bg = "rgba(56, 189, 248, 0.12)" if theme == 'dark' else "rgba(37, 99, 235, 0.08)"
    badge_border = "rgba(56, 189, 248, 0.3)" if theme == 'dark' else "rgba(37, 99, 235, 0.2)"
    badge_fg = "#38BDF8" if theme == 'dark' else "#1D4ED8"

    svg = f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 620 130" width="100%" height="100%">
  <defs>
    <!-- Gradients -->
    <linearGradient id="logoBgGrad_{theme}" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#0F172A"/>
      <stop offset="100%" stop-color="#1E293B"/>
    </linearGradient>
    <linearGradient id="pilotTextGrad_{theme}" x1="0%" y1="0%" x2="100%" y2="0%">
      <stop offset="0%" stop-color="#38BDF8"/>
      <stop offset="60%" stop-color="#60A5FA"/>
      <stop offset="100%" stop-color="#818CF8"/>
    </linearGradient>
    <linearGradient id="shieldLeft_{theme}" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#38BDF8"/>
      <stop offset="100%" stop-color="#1D4ED8"/>
    </linearGradient>
    <linearGradient id="shieldRight_{theme}" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#818CF8"/>
      <stop offset="100%" stop-color="#4F46E5"/>
    </linearGradient>
    <linearGradient id="spearGrad_{theme}" x1="50%" y1="0%" x2="50%" y2="100%">
      <stop offset="0%" stop-color="#FFFFFF"/>
      <stop offset="100%" stop-color="#38BDF8"/>
    </linearGradient>
    <filter id="logoShadow_{theme}" x="-10%" y="-10%" width="120%" height="120%">
      <feDropShadow dx="0" dy="4" stdDeviation="6" flood-color="#0284C7" flood-opacity="0.25"/>
    </filter>
  </defs>

  <!-- Mini Icon Mark (104x104 at x=12, y=13) -->
  <g transform="translate(10, 10)">
    <rect x="0" y="0" width="110" height="110" rx="26" fill="url(#logoBgGrad_{theme})" stroke="#38BDF8" stroke-opacity="0.3" stroke-width="1.5" filter="url(#logoShadow_{theme})"/>
    <g transform="translate(5, 5) scale(0.195)">
      <!-- Left Wing -->
      <path d="M256 108 L142 208 C134 215 130 225 130 236 L130 312 C130 326 142 336 156 332 L212 316 C218 314 224 316 228 320 L246 342 C251 348 255 352 256 354 L256 108 Z" fill="url(#shieldLeft_{theme})"/>
      <!-- Right Wing -->
      <path d="M256 108 L370 208 C378 215 382 225 382 236 L382 312 C382 326 370 336 356 332 L300 316 C294 314 288 316 284 320 L266 342 C261 348 257 352 256 354 L256 108 Z" fill="url(#shieldRight_{theme})"/>
      <!-- Center Spear -->
      <path d="M256 82 L290 190 C292 196 288 202 282 202 L266 202 L266 376 C266 384 256 388 256 388 C256 388 246 384 246 376 L246 202 L230 202 C224 202 220 196 222 190 L256 82 Z" fill="url(#spearGrad_{theme})"/>
      <!-- Core Diamond -->
      <polygon points="256,196 280,230 256,264 232,230" fill="#38BDF8"/>
      <polygon points="256,206 272,230 256,254 240,230" fill="#FFFFFF"/>
    </g>
  </g>

  <!-- Typography -->
  <g transform="translate(142, 0)">
    <!-- Main Name -->
    <text x="0" y="66" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif" font-size="46" font-weight="800" letter-spacing="-0.5">
      <tspan fill="{fg_title}">Clean</tspan><tspan fill="url(#pilotTextGrad_{theme})">Pilot</tspan>
    </text>

    <!-- Tagline -->
    <text x="2" y="94" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif" font-size="12" font-weight="600" fill="{sub_title}" letter-spacing="1.8">
      PRIVACY-FIRST SYSTEM &amp; STORAGE HUB
    </text>

    <!-- Zero Telemetry Badge -->
    <g transform="translate(244, 28)">
      <rect x="0" y="0" width="112" height="22" rx="11" fill="{badge_bg}" stroke="{badge_border}" stroke-width="1"/>
      <text x="56" y="15" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif" font-size="10" font-weight="700" fill="{badge_fg}" text-anchor="middle" letter-spacing="0.5">
        ZERO TELEMETRY
      </text>
    </g>
  </g>
</svg>'''
    return svg

def create_svg_banner():
    svg = '''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1280 640" width="100%" height="100%">
  <defs>
    <linearGradient id="bannerBg" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#090D16"/>
      <stop offset="40%" stop-color="#0F172A"/>
      <stop offset="100%" stop-color="#030712"/>
    </linearGradient>

    <radialGradient id="bannerGlow" cx="50%" cy="35%" r="60%">
      <stop offset="0%" stop-color="#1E3A8A" stop-opacity="0.45"/>
      <stop offset="50%" stop-color="#0F172A" stop-opacity="0.1"/>
      <stop offset="100%" stop-color="#090D16" stop-opacity="0"/>
    </radialGradient>

    <linearGradient id="bannerTextGrad" x1="0%" y1="0%" x2="100%" y2="0%">
      <stop offset="0%" stop-color="#38BDF8"/>
      <stop offset="50%" stop-color="#60A5FA"/>
      <stop offset="100%" stop-color="#A78BFA"/>
    </linearGradient>

    <filter id="bannerDrop" x="-10%" y="-10%" width="120%" height="120%">
      <feDropShadow dx="0" dy="16" stdDeviation="24" flood-color="#0284C7" flood-opacity="0.3"/>
    </filter>
  </defs>

  <!-- Background -->
  <rect width="1280" height="640" fill="url(#bannerBg)"/>
  <rect width="1280" height="640" fill="url(#bannerGlow)"/>

  <!-- Subtle Blueprint Tech Grid -->
  <g stroke="#38BDF8" stroke-width="1" opacity="0.04">
    <line x1="0" y1="80" x2="1280" y2="80"/>
    <line x1="0" y1="160" x2="1280" y2="160"/>
    <line x1="0" y1="240" x2="1280" y2="240"/>
    <line x1="0" y1="320" x2="1280" y2="320"/>
    <line x1="0" y1="400" x2="1280" y2="400"/>
    <line x1="0" y1="480" x2="1280" y2="480"/>
    <line x1="0" y1="560" x2="1280" y2="560"/>
    <line x1="160" y1="0" x2="160" y2="640"/>
    <line x1="320" y1="0" x2="320" y2="640"/>
    <line x1="480" y1="0" x2="480" y2="640"/>
    <line x1="640" y1="0" x2="640" y2="640"/>
    <line x1="800" y1="0" x2="800" y2="640"/>
    <line x1="960" y1="0" x2="960" y2="640"/>
    <line x1="1120" y1="0" x2="1120" y2="640"/>
  </g>

  <!-- Central Embellished Icon Mark (180x180 at x=550, y=70) -->
  <g transform="translate(550, 60)" filter="url(#bannerDrop)">
    <rect x="0" y="0" width="180" height="180" rx="42" fill="#0F172A" stroke="#38BDF8" stroke-width="2.5" stroke-opacity="0.4"/>
    <circle cx="90" cy="90" r="60" fill="#2563EB" opacity="0.25" filter="blur(16px)"/>
    <g transform="translate(10, 10) scale(0.3125)">
      <!-- Left Wing -->
      <path d="M256 108 L142 208 C134 215 130 225 130 236 L130 312 C130 326 142 336 156 332 L212 316 C218 314 224 316 228 320 L246 342 C251 348 255 352 256 354 L256 108 Z" fill="#2563EB"/>
      <!-- Right Wing -->
      <path d="M256 108 L370 208 C378 215 382 225 382 236 L382 312 C382 326 370 336 356 332 L300 316 C294 314 288 316 284 320 L266 342 C261 348 257 352 256 354 L256 108 Z" fill="#6366F1"/>
      <!-- Center Spear -->
      <path d="M256 82 L290 190 C292 196 288 202 282 202 L266 202 L266 376 C266 384 256 388 256 388 C256 388 246 384 246 376 L246 202 L230 202 C224 202 220 196 222 190 L256 82 Z" fill="#E0F2FE"/>
      <!-- Diamond Core -->
      <polygon points="256,196 280,230 256,264 232,230" fill="#38BDF8"/>
      <polygon points="256,206 272,230 256,254 240,230" fill="#FFFFFF"/>
    </g>
  </g>

  <!-- Main Headline -->
  <text x="640" y="300" text-anchor="middle" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif" font-size="64" font-weight="900" letter-spacing="-1">
    <tspan fill="#F8FAFC">Clean</tspan><tspan fill="url(#bannerTextGrad)">Pilot</tspan>
  </text>

  <!-- Tagline -->
  <text x="640" y="348" text-anchor="middle" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif" font-size="22" font-weight="500" fill="#94A3B8" letter-spacing="0.5">
    Honest, Privacy-First Desktop Disk Analyzer &amp; System Maintenance Hub
  </text>

  <!-- Badges Row -->
  <g transform="translate(640, 400)">
    <!-- 100% Offline -->
    <g transform="translate(-370, 0)">
      <rect x="0" y="0" width="165" height="38" rx="19" fill="rgba(34, 197, 94, 0.12)" stroke="rgba(34, 197, 94, 0.35)" stroke-width="1.2"/>
      <circle cx="22" cy="19" r="5" fill="#22C55E"/>
      <text x="96" y="24" text-anchor="middle" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif" font-size="13" font-weight="700" fill="#86EFAC">100% OFFLINE</text>
    </g>

    <!-- Zero Telemetry -->
    <g transform="translate(-185, 0)">
      <rect x="0" y="0" width="175" height="38" rx="19" fill="rgba(56, 189, 248, 0.12)" stroke="rgba(56, 189, 248, 0.35)" stroke-width="1.2"/>
      <circle cx="22" cy="19" r="5" fill="#38BDF8"/>
      <text x="100" y="24" text-anchor="middle" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif" font-size="13" font-weight="700" fill="#BAE6FD">ZERO TELEMETRY</text>
    </g>

    <!-- Java 21 & Virtual Threads -->
    <g transform="translate(10, 0)">
      <rect x="0" y="0" width="180" height="38" rx="19" fill="rgba(168, 85, 247, 0.12)" stroke="rgba(168, 85, 247, 0.35)" stroke-width="1.2"/>
      <circle cx="22" cy="19" r="5" fill="#A855F7"/>
      <text x="103" y="24" text-anchor="middle" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif" font-size="13" font-weight="700" fill="#E9D5FF">JAVA 21 VIRTUAL</text>
    </g>

    <!-- OSHI Hardware Telemetry -->
    <g transform="translate(210, 0)">
      <rect x="0" y="0" width="170" height="38" rx="19" fill="rgba(249, 115, 22, 0.12)" stroke="rgba(249, 115, 22, 0.35)" stroke-width="1.2"/>
      <circle cx="22" cy="19" r="5" fill="#F97316"/>
      <text x="98" y="24" text-anchor="middle" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif" font-size="13" font-weight="700" fill="#FED7AA">OSHI HARDWARE</text>
    </g>
  </g>

  <!-- Core Capability Cards Row -->
  <g transform="translate(140, 480)">
    <!-- Card 1 -->
    <g transform="translate(0, 0)">
      <rect width="230" height="84" rx="14" fill="#0F172A" stroke="#334155" stroke-width="1"/>
      <text x="20" y="34" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif" font-size="14" font-weight="700" fill="#38BDF8">🛡 Threat Hunter</text>
      <text x="20" y="58" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif" font-size="12" font-weight="400" fill="#94A3B8">Crypto-miners &amp; LOLBins</text>
    </g>

    <!-- Card 2 -->
    <g transform="translate(255, 0)">
      <rect width="230" height="84" rx="14" fill="#0F172A" stroke="#334155" stroke-width="1"/>
      <text x="20" y="34" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif" font-size="14" font-weight="700" fill="#38BDF8">💾 Streaming Hash Dedupe</text>
      <text x="20" y="58" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif" font-size="12" font-weight="400" fill="#94A3B8">Direct ByteBuffer NIO.2</text>
    </g>

    <!-- Card 3 -->
    <g transform="translate(510, 0)">
      <rect width="230" height="84" rx="14" fill="#0F172A" stroke="#334155" stroke-width="1"/>
      <text x="20" y="34" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif" font-size="14" font-weight="700" fill="#38BDF8">💻 Laptop Diagnostics</text>
      <text x="20" y="58" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif" font-size="12" font-weight="400" fill="#94A3B8">Battery wear &amp; thermals</text>
    </g>

    <!-- Card 4 -->
    <g transform="translate(765, 0)">
      <rect width="230" height="84" rx="14" fill="#0F172A" stroke="#334155" stroke-width="1"/>
      <text x="20" y="34" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif" font-size="14" font-weight="700" fill="#38BDF8">🛠 Developer Cache</text>
      <text x="20" y="58" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif" font-size="12" font-weight="400" fill="#94A3B8">node_modules, gradle, venv</text>
    </g>
  </g>
</svg>'''
    return svg

def main():
    logo_dir = "docs/assets/logo"
    icons_res_dir = "src/main/resources/icons"
    os.makedirs(logo_dir, exist_ok=True)
    os.makedirs(icons_res_dir, exist_ok=True)

    # 1. Write SVGs
    icon_svg = create_svg_icon()
    with open(f"{logo_dir}/cleanpilot-icon.svg", "w", encoding="utf-8") as f:
        f.write(icon_svg)
    with open(f"{icons_res_dir}/cleanpilot-icon.svg", "w", encoding="utf-8") as f:
        f.write(icon_svg)

    logo_dark = create_svg_logo_horizontal("dark")
    with open(f"{logo_dir}/cleanpilot-logo-dark.svg", "w", encoding="utf-8") as f:
        f.write(logo_dark)

    logo_light = create_svg_logo_horizontal("light")
    with open(f"{logo_dir}/cleanpilot-logo-light.svg", "w", encoding="utf-8") as f:
        f.write(logo_light)

    banner_svg = create_svg_banner()
    with open(f"{logo_dir}/cleanpilot-banner.svg", "w", encoding="utf-8") as f:
        f.write(banner_svg)

    print("Successfully generated SVG vector assets.")

    # 2. Process High-Res Master Image and generate PNGs + Multi-resolution ICO
    # Check source image from image-generator
    subagent_img = r"C:\Users\Anas\.gemini\antigravity\brain\471928e7-a1bb-49eb-9129-a0cf83000778\cleanpilot_icon_1792844435769.jpg"
    if os.path.exists(subagent_img):
        src_img = Image.open(subagent_img).convert("RGBA")
        
        # Apply smooth rounded corner squircle mask
        w, h = src_img.size
        mask = Image.new("L", (w, h), 0)
        draw = ImageDraw.Draw(mask)
        # Rounded squircle radius ~ 22% of width
        corner_radius = int(w * 0.22)
        draw.rounded_rectangle([(0, 0), (w, h)], radius=corner_radius, fill=255)
        
        # Composite with mask
        output_img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        output_img.paste(src_img, (0, 0), mask=mask)

        # Save 1024x1024 master PNG
        output_img.save(f"{logo_dir}/cleanpilot-app-icon-1024.png", format="PNG", optimize=True)
        print("Saved cleanpilot-app-icon-1024.png")

        # Generate multi-resolution PNGs
        sizes = [512, 256, 128, 64, 48, 32, 16]
        ico_images = []
        for s in sizes:
            resized = output_img.resize((s, s), Image.Resampling.LANCZOS)
            resized.save(f"{logo_dir}/cleanpilot-{s}x{s}.png", format="PNG", optimize=True)
            resized.save(f"{icons_res_dir}/cleanpilot-{s}x{s}.png", format="PNG", optimize=True)
            ico_images.append(resized)
            print(f"Generated cleanpilot-{s}x{s}.png")

        # Save Windows Multi-Icon ICO file containing [256, 128, 64, 48, 32, 16]
        # Pillow save to ICO accepts sizes parameter or saves list of images
        ico_sizes = [(256, 256), (128, 128), (64, 64), (48, 48), (32, 32), (16, 16)]
        output_img.save(
            f"{logo_dir}/cleanpilot.ico",
            format="ICO",
            sizes=ico_sizes
        )
        output_img.save(
            f"{icons_res_dir}/cleanpilot.ico",
            format="ICO",
            sizes=ico_sizes
        )
        # Also favicon
        output_img.save(
            f"{logo_dir}/favicon.ico",
            format="ICO",
            sizes=[(32, 32), (16, 16)]
        )
        print("Generated cleanpilot.ico (multi-resolution Windows ICO) and favicon.ico.")

if __name__ == "__main__":
    main()
