
window.InfinityThinking = {
  el: null,
  mount: function(parent) {
    var w = document.createElement('div');
    w.className = 'infinity-thinking';
    w.innerHTML = '<span class="dot"></span><span class="dot"></span><span class="dot"></span>';
    (parent || document.body).appendChild(w);
    this.el = w;
    return w;
  },
  show: function() { if (this.el) this.el.classList.add('on'); },
  hide: function() { if (this.el) this.el.classList.remove('on'); }
};
