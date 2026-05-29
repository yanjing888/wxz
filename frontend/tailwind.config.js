/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{vue,js}'],
  theme: {
    extend: {
      colors: {
        accent: {
          blue: '#2c6fb8',
          green: '#10b981'
        }
      }
    }
  },
  plugins: []
}
