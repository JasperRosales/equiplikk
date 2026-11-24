import { state } from './state.js';

let renderer = () => {};
let listenerAdded = false;

function parse() {
  const raw = location.hash.replace(/^#\/?/, '') || 'dashboard';
  const [route, query = ''] = raw.split('?');
  state.route = route || 'dashboard';
  state.params = new URLSearchParams(query);
}

export const router = {
  start(render) {
    renderer = render;
    if (!listenerAdded) {
      window.addEventListener('hashchange', () => {
        parse();
        renderer();
      });
      listenerAdded = true;
    }
    parse();
  },

  go(path) {
    const target = '#/' + String(path).replace(/^#?\/?/, '');
    if (location.hash === target) {
      state.routeVersion++;
      renderer();
    } else {
      location.hash = target;
    }
    document.getElementById('sidebar')?.classList.remove('open');
    document.getElementById('navBackdrop')?.classList.add('hidden');
  },

  current: () => state.route
};
