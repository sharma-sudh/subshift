-- =====================================================================
-- SubShift: classifier training data, BATCH 1 (40 synthetic emails)
-- 20 x RENEWAL, 20 x NOT_RENEWAL
--
-- BEFORE RUNNING:
--   1. Replace 'your-email@example.com' below with the email of the
--      user who is labeling this data (it must exist in the users table).
--   2. Run against subshift_db (psql -f or pgAdmin Query Tool).
--
-- The whole script runs as one block: if anything fails, nothing is inserted.
-- Email text is stored as "Subject: ...", a blank line, then the body.
-- =====================================================================

DO $do$
DECLARE
    labeler BIGINT;
BEGIN
    SELECT id INTO labeler FROM users WHERE email = 'your-email@example.com';
    IF labeler IS NULL THEN
        RAISE EXCEPTION 'Labeler user not found. Edit the email at the top of this script.';
    END IF;

    INSERT INTO classifier_training_emails (email_text, label, labeled_by) VALUES

    -- =================================================================
    -- RENEWAL (20)
    -- =================================================================

    -- R1 Netflix payment
    ($e$Subject: Your Netflix payment was successful

Hi Priya,

Thanks for being a Netflix member. We've successfully processed your payment of ₹649.00 for your Premium plan.

Billing date: 03 October 2026
Next billing date: 03 November 2026
Payment method: Visa ending in 4821

You can change your plan or cancel anytime from Account settings.

- The Netflix Team$e$, 'RENEWAL', labeler),

    -- R2 Spotify receipt
    ($e$Subject: Your Spotify Premium receipt

Hi Ananya,

Thanks for your payment. Here's your receipt.

Spotify Premium Individual - ₹119.00
Charged on: 12 Oct 2026

Your plan renews automatically every month. Your next payment will be on 12 Nov 2026. You can cancel any time before then.

Spotify$e$, 'RENEWAL', labeler),

    -- R3 Google One
    ($e$Subject: Google One: your subscription has been renewed

Your Google One subscription has been renewed.

Plan: 200 GB
Amount: ₹130.00 / month
Renewed on: 7 Oct 2026
Next renewal: 7 Nov 2026
Order number: GPA.3321-8847-2210-55193

To manage or cancel your membership, go to one.google.com.

Google Payments$e$, 'RENEWAL', labeler),

    -- R4 cult.fit gym
    ($e$Subject: Membership renewed - cult.fit

Hi Karthik,

Your cult.fit ELITE membership has been renewed successfully.

Amount paid: ₹1,499 (monthly pack)
Valid until: 14 Nov 2026
Paid via: UPI

See you at the centre!
Team cult.fit$e$, 'RENEWAL', labeler),

    -- R5 Amazon Prime
    ($e$Subject: Your Amazon Prime membership has been renewed

Hello Suresh,

Your annual Amazon Prime membership fee of ₹1,499.00 was charged to your card ending in 7702 on 8 October 2026.

Your membership is now valid through 7 October 2027. Enjoy free delivery, Prime Video and more.

If you do not want your membership to renew automatically, you can turn off auto-renewal in Your Prime Membership.

Amazon.in$e$, 'RENEWAL', labeler),

    -- R6 YouTube Premium
    ($e$Subject: Receipt for your YouTube Premium membership

Thanks for your payment.

YouTube Premium - Individual (monthly)
Amount: ₹129.00
Billed on: 01 Oct 2026
Next charge: 01 Nov 2026

You can manage or cancel your membership under Paid memberships in YouTube.

The YouTube Team$e$, 'RENEWAL', labeler),

    -- R7 Hotstar upcoming auto-renewal
    ($e$Subject: Your subscription will renew soon

Hi Vikram,

This is a reminder that your Disney+ Hotstar Super plan will auto-renew on 20 October 2026. You will be charged ₹899.00 for the next year.

If you don't want to be charged, please cancel before the renewal date from your account settings.

Disney+ Hotstar$e$, 'RENEWAL', labeler),

    -- R8 Apple receipt
    ($e$Subject: Your receipt from Apple.

Receipt
Order ID: MQ8KD2LX9R
Date: Oct 5, 2026

iCloud+ with 50 GB of storage (Monthly)
Renews Nov 5, 2026
₹75.00

TOTAL ₹75.00
Billed to: Visa .... 4821

If you didn't authorize this charge, contact Apple Support.$e$, 'RENEWAL', labeler),

    -- R9 Microsoft 365 upcoming auto-renewal
    ($e$Subject: Your Microsoft 365 subscription will auto-renew

Hello,

Your Microsoft 365 Personal subscription is set to renew on 28 October 2026. We'll charge ₹489.00 to the card ending in 3345.

To avoid being charged, turn off recurring billing in your Microsoft account before that date.

Microsoft Store$e$, 'RENEWAL', labeler),

    -- R10 Adobe invoice
    ($e$Subject: Thanks for your payment - Adobe invoice IN-20261009

Hi Neha,

We've received your payment. Your invoice is below.

Creative Cloud Photography plan (monthly)
Billing period: 9 Oct 2026 - 8 Nov 2026
Subtotal: ₹1,420.34
GST (18%): ₹255.66
Total: ₹1,676.00

Your plan will renew on 9 November 2026.

Adobe Systems$e$, 'RENEWAL', labeler),

    -- R11 Notion receipt (USD)
    ($e$Subject: Receipt from Notion Labs, Inc. #2214-8830

Receipt #2214-8830
Date paid: Oct 2, 2026

Notion Plus plan - monthly, 1 member
Amount paid: $10.00

Your next invoice will be on Nov 2, 2026. Manage your plan in Settings > Billing.$e$, 'RENEWAL', labeler),

    -- R12 Duolingo
    ($e$Subject: Your Super Duolingo subscription has been renewed!

Hi Meera,

Great news: your Super Duolingo subscription renewed today, so you can keep learning without ads or hearts running out!

Plan: Super (annual)
Amount charged: ₹799.00
Renewal date: 17 Oct 2026

Keep that streak alive!
The Duolingo Team$e$, 'RENEWAL', labeler),

    -- R13 Gold's Gym receipt
    ($e$Subject: Payment confirmation - Gold's Gym membership fee

Dear Member,

We have received ₹3,500 towards your quarterly membership fee.

Receipt No: GG/2026/10492
Paid on: 11/10/2026
Membership valid till: 10/01/2027

Thank you for choosing Gold's Gym, RS Puram.$e$, 'RENEWAL', labeler),

    -- R14 Dropbox renewal with price increase
    ($e$Subject: Your Dropbox Plus plan renewal and upcoming price change

Hi Arun,

Your Dropbox Plus plan will renew on 22 November 2026. With this renewal, the price of your plan will increase from $11.99 to $12.99 per month.

You don't need to do anything to continue with your plan. If you'd like to change or cancel it, visit your account page before the renewal date.

The Dropbox Team$e$, 'RENEWAL', labeler),

    -- R15 Audible
    ($e$Subject: Your Audible membership payment

Hello,

We've charged ₹199.00 for your monthly Audible membership on 15 Oct 2026. Your monthly credit has been added to your library.

Your membership will renew again on 15 Nov 2026.

Audible India$e$, 'RENEWAL', labeler),

    -- R16 LinkedIn Premium
    ($e$Subject: Your receipt from LinkedIn

Thanks for your purchase, Rohan.

Receipt #LI-20261006-77412
LinkedIn Premium Career (monthly)
Purchased: 6 Oct 2026
Price: ₹1,999.00

Your subscription renews on 6 Nov 2026 unless you cancel. To manage your subscription, go to Settings.$e$, 'RENEWAL', labeler),

    -- R17 Canva Pro
    ($e$Subject: Canva Pro - payment received

Hi Meera,

We've received your payment of ₹499.00 for Canva Pro (monthly, 1 person).

Your next billing date is 25 October 2026.

Questions about your plan? Visit canva.com/help.
Canva$e$, 'RENEWAL', labeler),

    -- R18 Netflix price change notice
    ($e$Subject: An update on your Netflix membership price

Hi Priya,

We're writing to let you know that the price of your Standard plan is changing from ₹499 to ₹549 per month.

The new price will apply from your next billing date on 18 November 2026. You can change or cancel your plan any time in Account settings.

- The Netflix Team$e$, 'RENEWAL', labeler),

    -- R19 SonyLIV renewal reminder (ALL CAPS subject)
    ($e$Subject: RENEWAL REMINDER: YOUR SONYLIV PREMIUM EXPIRES IN 3 DAYS

Dear Subscriber,

Your SonyLIV Premium annual plan expires on 16 October 2026. Renew now for ₹999 to continue watching without interruption.

Auto-renew is currently ON. You will be charged on the expiry date.

Team SonyLIV$e$, 'RENEWAL', labeler),

    -- R20 HealthifyMe
    ($e$Subject: HealthifyMe Pro: payment successful

Hi Sneha,

Your HealthifyMe Pro plan (3 months) has been renewed.

Amount paid: ₹1,499
Paid via: UPI on 09 Oct 2026
Valid until: 9 Jan 2027

Keep going, you're doing great!
Team HealthifyMe$e$, 'RENEWAL', labeler),

    -- =================================================================
    -- NOT_RENEWAL (20)
    -- =================================================================

    -- N1 personal
    ($e$Subject: Dinner on Saturday?

Hey Arjun,

Are you free Saturday night? A few of us are planning to try that new biryani place near RS Puram around 8. Let me know and I'll book a table.

- Dev$e$, 'NOT_RENEWAL', labeler),

    -- N2 college notes
    ($e$Subject: Notes from Tuesday's lecture

Hi Divya,

I've attached my notes from the DBMS lecture. The normalization part is on pages 4 to 7. Can you send me yours for the transactions section? I missed the last 20 minutes.

Thanks!
Kavya$e$, 'NOT_RENEWAL', labeler),

    -- N3 newsletter
    ($e$Subject: The Weekly Byte: AI tools, startup funding and more

Good morning!

This week in tech:
- Three new open-source tools developers are talking about
- A Bengaluru startup raises its Series A
- How to write cleaner commit messages

Read the full stories on our website. Forward this to a friend who'd enjoy it!

Unsubscribe | Manage preferences$e$, 'NOT_RENEWAL', labeler),

    -- N4 Spotify marketing (same brand, not renewal)
    ($e$Subject: New music for you this Friday

Hi Ananya,

Your Release Radar is ready. We've put together new songs from artists you follow, plus a few picks we think you'll love.

Listen now in the Spotify app.

Spotify$e$, 'NOT_RENEWAL', labeler),

    -- N5 Netflix marketing (same brand, not renewal)
    ($e$Subject: New on Netflix this week

Hi Priya,

Here's what's coming to Netflix: a gripping new thriller series, a feel-good comedy special, and the final season of a fan favourite.

Watch trailers and add titles to My List.

- The Netflix Team$e$, 'NOT_RENEWAL', labeler),

    -- N6 food delivery receipt
    ($e$Subject: Your Swiggy order was delivered

Hi Rahul,

Your order from Anjappar Chettinad Restaurant was delivered at 8:42 PM.

Chicken Biryani x 2
Order total: ₹342.00
Paid via: UPI

Rate your order and help us improve!$e$, 'NOT_RENEWAL', labeler),

    -- N7 shipping notification
    ($e$Subject: Your order has been shipped

Hello Suresh,

Good news! Your package is on its way.

Item: boAt Rockerz 255 Wireless Earphones
Order total: ₹1,299.00
Arriving: Thursday, 15 October

Track your package in Your Orders.

Amazon.in$e$, 'NOT_RENEWAL', labeler),

    -- N8 ride receipt
    ($e$Subject: Your Tuesday evening trip with Murugan

Thanks for riding, Karthik.

Total: ₹186.50
Trip: Gandhipuram to Peelamedu
Distance: 9.4 km, 31 min
Paid via: Cash

Rate your driver to help us improve.$e$, 'NOT_RENEWAL', labeler),

    -- N9 service appointment
    ($e$Subject: Your RO service appointment is confirmed

Dear Customer,

Your water purifier service visit is scheduled for Saturday, 17 October between 10 AM and 12 PM. Our technician will call you before arriving.

Please keep the unit accessible. To reschedule, call our helpline.

Customer Care$e$, 'NOT_RENEWAL', labeler),

    -- N10 bank statement ready
    ($e$Subject: Your account statement for September 2026 is ready

Dear Customer,

Your monthly e-statement for the period 01 Sep 2026 to 30 Sep 2026 is attached. The file is password protected.

Never share your password, OTP or PIN with anyone, including bank staff.

State Bank of India$e$, 'NOT_RENEWAL', labeler),

    -- N11 OTP
    ($e$Subject: Your one-time password

482913 is your OTP to verify your login. It is valid for 10 minutes. Do not share this code with anyone.

If you did not request this, please ignore this email.$e$, 'NOT_RENEWAL', labeler),

    -- N12 security alert
    ($e$Subject: Security alert: new sign-in to your Google Account

Hi Priya,

Your Google Account was just signed in to from a new Windows device.

Location: Coimbatore, India
Time: 12 Oct 2026, 9:14 PM

If this was you, you don't need to do anything. If not, secure your account right away.

The Google Accounts Team$e$, 'NOT_RENEWAL', labeler),

    -- N13 job interview
    ($e$Subject: Interview invitation: Software Engineering Intern

Dear Candidate,

Thank you for applying. We would like to invite you for a technical interview on Monday, 19 October at 11:00 AM via Google Meet.

Please confirm your availability by replying to this email and keep your resume ready.

Best regards,
Talent Acquisition Team$e$, 'NOT_RENEWAL', labeler),

    -- N14 sale promo
    ($e$Subject: FLAT 50% OFF - Big Festive Sale ends tonight!

Don't miss out!

Up to 50% off on fashion, electronics and home essentials. Extra 10% off with select bank cards. Sale ends at midnight.

Shop now before your favourites are gone!

Terms and conditions apply.$e$, 'NOT_RENEWAL', labeler),

    -- N15 college notice
    ($e$Subject: Assignment submission deadline extended

Dear students,

The deadline for the Operating Systems assignment has been extended to 21 October, 5 PM. Please submit your PDF on the college portal.

No further extensions will be given.

Prof. Ramesh$e$, 'NOT_RENEWAL', labeler),

    -- N16 refund processed
    ($e$Subject: Your refund of ₹1,299 has been processed

Hi Rahul,

We have processed the refund for your returned item. The amount will reach your original payment method within 5 to 7 business days.

Refund amount: ₹1,299.00
Order ID: OD33827714502

Flipkart$e$, 'NOT_RENEWAL', labeler),

    -- N17 meeting reschedule
    ($e$Subject: Team standup moved to 11 AM

Hi all,

Tomorrow's standup is moved to 11 AM because of the client call. Same Meet link as always. Please add your updates to the shared doc before the meeting.

Thanks,
Sanjay$e$, 'NOT_RENEWAL', labeler),

    -- N18 welcome email (brand, not renewal)
    ($e$Subject: Welcome to Duolingo!

Hi Meera,

You've just taken the first step toward learning a new language. Pick a goal, take your first lesson and start building your streak.

Just five minutes a day makes a difference. Open the app to begin.

The Duolingo Team$e$, 'NOT_RENEWAL', labeler),

    -- N19 storage warning / upsell (not a renewal)
    ($e$Subject: Your Google storage is almost full

Hi Arun,

You've used 92% of your 15 GB of storage. When you run out, you won't be able to save new files or receive emails.

You can free up space by deleting large files, or learn more about storage plans.

Google$e$, 'NOT_RENEWAL', labeler),

    -- N20 train ticket
    ($e$Subject: E-ticket booking confirmation - PNR 4521897630

Dear Passenger,

Your ticket has been booked.

Train: Kovai Express (12675)
From: Coimbatore Jn (CBE) to Chennai Central (MAS)
Date of journey: 18 Oct 2026
Class: 3A, Berth: Confirmed
Total fare: ₹745.00

Carry a valid photo ID during travel. Wishing you a happy journey!
IRCTC$e$, 'NOT_RENEWAL', labeler);

    RAISE NOTICE 'Batch 1 inserted: 40 rows (20 RENEWAL, 20 NOT_RENEWAL).';
END
$do$;

-- Check after running:
-- SELECT label, COUNT(*) FROM classifier_training_emails GROUP BY label;
