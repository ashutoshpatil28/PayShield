import { expect, test } from '@playwright/test';

test('login, navigate operations console, and initiate payment', async ({ page }) => {
  await page.goto('/login');
  await expect(page.getByText('PayShield Operations')).toBeVisible();

  await page.getByRole('button', { name: /Sign in to Console/i }).click();
  await expect(page.getByRole('heading', { name: 'Operations Overview' })).toBeVisible();

  // Navigate to Payments and initiate a simulated payment
  await page.getByRole('link', { name: 'Payments' }).click();
  await expect(page.getByRole('heading', { name: 'Payments' })).toBeVisible();

  await page.getByRole('button', { name: /Initiate Payment/i }).click();
  await page.getByRole('button', { name: /Submit Payment/i }).click();

  await expect(page.locator('tbody').getByText('SUCCEEDED').first()).toBeVisible();

  // Navigate to Ledger & Audit Trail
  await page.getByRole('link', { name: 'Ledger' }).click();
  await expect(page.getByRole('heading', { name: 'Double-Entry Financial Ledger' })).toBeVisible();

  await page.getByRole('link', { name: 'Audit Trail' }).click();
  await expect(page.getByRole('heading', { name: 'Audit Trail' })).toBeVisible();
});
