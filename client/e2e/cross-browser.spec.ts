import { expect, test } from '@playwright/test'

test('public rendering and anonymous private deep links work without browser credential storage',async({page})=>{
  await page.goto('/')
  await expect(page.getByRole('heading',{name:/Build agents/})).toBeVisible()
  await page.setViewportSize({width:390,height:844})
  await expect(page.getByRole('link',{name:'Create your workspace'})).toBeVisible()
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBe(true)
  await page.goto('/projects/cross-browser-private-fixture')
  await expect(page).toHaveURL(/\/login\?returnTo=/)
  await expect(page.getByRole('heading',{name:'Welcome back.'})).toBeVisible()
  await page.reload();await expect(page.getByRole('heading',{name:'Welcome back.'})).toBeVisible()
  expect(await page.evaluate(()=>({local:localStorage.length,session:sessionStorage.length}))).toEqual({local:0,session:0})
})
