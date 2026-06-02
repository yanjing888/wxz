/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{vue,js}'],
  theme: {
    extend: {
      colors: {
        accent: {
          blue: '#4f46e5',
          green: '#10b981',
          cyan: '#06b6d4',
          violet: '#8b5cf6'
        },
        brand: {
          50: '#eef2ff',
          100: '#e0e7ff',
          200: '#c7d2fe',
          300: '#a5b4fc',
          400: '#818cf8',
          500: '#6366f1',
          600: '#4f46e5',
          700: '#4338ca',
          800: '#3730a3',
          900: '#312e81'
        },
        surface: {
          base: '#eef1f8',
          card: '#ffffff',
          soft: '#f5f7fc',
          muted: '#eef2f9'
        },
        line: {
          soft: '#e4e9f3',
          strong: '#cfd7e6'
        },
        ink: {
          strong: '#0f172a',
          base: '#1e293b',
          muted: '#64748b',
          faint: '#94a3b8'
        }
      },
      boxShadow: {
        card: '0 1px 3px rgba(15, 23, 42, 0.05), 0 1px 2px rgba(15, 23, 42, 0.03)',
        soft: '0 4px 14px rgba(30, 41, 89, 0.06), 0 2px 4px rgba(15, 23, 42, 0.03)',
        lift: '0 12px 32px rgba(30, 41, 89, 0.10), 0 4px 12px rgba(15, 23, 42, 0.04)',
        brand: '0 8px 24px rgba(79, 70, 229, 0.22)'
      },
      borderRadius: {
        '2xl': '18px',
        '3xl': '24px'
      }
    }
  },
  plugins: []
}
