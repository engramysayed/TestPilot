(function () {
  function ensureMainTarget() {
    var main = document.querySelector('main.shell, main.loginpage, main.keel-page');
    if (main && !main.id) {
      main.id = 'keel-main-content';
    }
    if (!main) {
      main = document.querySelector('main');
      if (main && !main.id) main.id = 'keel-main-content';
    }
  }

  document.addEventListener('DOMContentLoaded', ensureMainTarget);
})();