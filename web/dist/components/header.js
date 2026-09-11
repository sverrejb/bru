// @ts-check

import { loadPhone } from '../util.js';

const LINKS = [
  ['/selfhost/', 'Self host'],
  ['/licenses/', 'About'],
  ['/privacy/', 'Privacy'],
];

customElements.define('site-header', class extends HTMLElement {
  connectedCallback() {
    const subtitle = this.getAttribute('subtitle');
    const links = loadPhone() ? [['/phone/', 'Your phone'], ...LINKS] : LINKS;
    this.innerHTML = `
<header>
  <a href="/"><h1>Bru</h1></a>
  <nav class="nav-links">
    ${links.map(([href, text]) => `<a href="${href}">${text}</a>`).join('\n    ')}
  </nav>
  ${subtitle ? `<span class="subtitle">${subtitle}</span>` : ''}
</header>`;
  }
});
