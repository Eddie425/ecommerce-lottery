INSERT INTO campaigns (name, max_draws_per_user, no_win_probability_basis_points)
VALUES ('Local Demo Campaign', 10, 5000);

INSERT INTO campaign_prizes (campaign_id, name, stock_remaining, probability_basis_points)
SELECT id, 'Wireless Headphones', 5, 500
FROM campaigns
WHERE name = 'Local Demo Campaign';

INSERT INTO campaign_prizes (campaign_id, name, stock_remaining, probability_basis_points)
SELECT id, 'Coffee Voucher', 20, 1500
FROM campaigns
WHERE name = 'Local Demo Campaign';

INSERT INTO campaign_prizes (campaign_id, name, stock_remaining, probability_basis_points)
SELECT id, 'Bonus Points', 100, 3000
FROM campaigns
WHERE name = 'Local Demo Campaign';
