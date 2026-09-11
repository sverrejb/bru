// @ts-check

const HTML = `
<footer>
  <p>Made in Norway 🇳🇴</p>
  <ul class="footer-links">
    <li>
      <a href="https://github.com/sverrejb/bru" target="_blank" rel="noopener">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M9 18l-6-6 6-6M15 6l6 6-6 6" />
        </svg>
        GitHub
      </a>
    </li>
    <li>
      <a href="https://bsky.app/profile/sverre.me" target="_blank" rel="noopener">
        <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
          <path d="M5.202 2.857C7.954 4.922 10.913 9.11 12 11.358c1.087-2.247 4.046-6.436 6.798-8.501C20.783 1.366 24 .213 24 3.883c0 .732-.42 6.156-.667 7.037-.856 3.061-3.978 3.842-6.755 3.37 4.854.826 6.089 3.562 3.422 6.299-5.065 5.196-7.28-1.304-7.847-2.97-.104-.305-.152-.448-.153-.327 0-.121-.05.022-.153.327-.568 1.666-2.782 8.166-7.847 2.97-2.667-2.737-1.432-5.473 3.422-6.3-2.777.473-5.899-.308-6.755-3.369C.42 10.04 0 4.615 0 3.883c0-3.67 3.217-2.517 5.202-1.026" />
        </svg>
        Bluesky
      </a>
    </li>
    <li>
      <a href="mailto:sverrejb@gmail.com">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <rect x="3" y="5" width="18" height="14" rx="2" />
          <path d="M3 7l9 6 9-6" />
        </svg>
        Email
      </a>
    </li>
  </ul>
</footer>`;

customElements.define('site-footer', class extends HTMLElement {
  connectedCallback() {
    this.innerHTML = HTML;
  }
});
