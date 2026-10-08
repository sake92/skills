<script setup>
defineProps({ profile: { type: Object, required: true } })
</script>

<template>
  <article aria-label="Public profile">
    <img v-if="profile.avatarUrl" :src="profile.avatarUrl" :alt="profile.displayName" />
    <h1>{{ profile.displayName }}</h1>
    <p>{{ profile.bio }}</p>
    <a :href="`/profiles/${profile.id}`">Profile link</a>
  </article>
</template>
