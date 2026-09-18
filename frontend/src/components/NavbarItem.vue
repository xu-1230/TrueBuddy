<script setup>

</script>

<template>
  <nav class="navbar">
    <div class="navbar__container">
      <div class="navbar__left">
        <a class="navbar__brand" href="/" aria-label="TrueBuddy 首页">
          <svg class="navbar__logo" viewBox="0 0 120 120" aria-hidden="true">
            <rect x="24" y="37" width="16" height="62" fill="#111" />
            <line x1="24" y1="44" x2="40" y2="44" stroke="#fff" stroke-width="3" />
            <rect x="46" y="22" width="16" height="74" fill="none" stroke="#111" stroke-width="6" />
            <rect x="68" y="47" width="16" height="52" fill="#111" />
            <line x1="67" y1="55" x2="85" y2="55" stroke="#fff" stroke-width="3" />
            <rect x="90" y="28" width="16" height="68" fill="none" stroke="#111" stroke-width="6" />
          </svg>
          <span class="navbar__name">TrueBuddy</span>
        </a>
        <a class="navbar__link" href="/docs">文档</a>
      </div>
      <div class="navbar__right">
        <a class="navbar__login" href="/login">登录</a>
      </div>
    </div>
  </nav>
  
</template>

<style scoped>
.navbar {
  position: sticky;
  top: 0;
  z-index: 100;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
}

.navbar__container {
  display: flex;
  align-items: center;
  justify-content: space-between;
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 1.5rem;
  height: 4rem;
}

.navbar__left {
  display: flex;
  align-items: center;
  gap: 2rem;
}

.navbar__brand {
  display: flex;
  align-items: center;
  text-decoration: none;
  color: inherit;
}

.navbar__logo {
  height: 2.5rem;
  width: auto;
  margin-right: 0.5rem;
  flex: 0 0 auto;
}

.navbar__name {
  font-size: 1.25rem;
  font-weight: 700;
  color: #111827;
}

.navbar__link {
  text-decoration: none;
  color: #4b5563;
  font-size: 0.95rem;
  font-weight: 500;
  transition: color 0.2s ease;
}

.navbar__link:hover {
  color: #111827;
}

.navbar__right {
  display: flex;
  align-items: center;
}

.navbar__login {
  text-decoration: none;
  padding: 0.5rem 1.25rem;
  border-radius: 999px;
  font-size: 0.95rem;
  font-weight: 600;
  color: #fff;
  background: #111827;
  transition: background 0.2s ease;
}

.navbar__login:hover {
  background: #374151;
}
</style>