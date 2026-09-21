from PIL import Image, ImageFilter, ImageDraw

def blur_region(image, box, radius=18):
    # box is (x1, y1, x2, y2)
    crop = image.crop(box)
    blurred = crop.filter(ImageFilter.GaussianBlur(radius=radius))
    image.paste(blurred, box)

# 1. Blur tab_personal.jpg
img_personal = Image.open('assets/tab_personal.jpg')
# Blur all contact names & snippet texts: x from 85 to 445, y from 130 to 930
blur_region(img_personal, (85, 130, 445, 930), radius=18)
# Also blur the bottom floating screenshot thumbnail at (30, 740, 135, 910)
blur_region(img_personal, (30, 740, 135, 910), radius=25)
img_personal.save('assets/tab_personal.jpg', quality=95)

# 2. Blur tab_otp.jpg
img_otp = Image.open('assets/tab_otp.jpg')
# Blur all contact names & snippet texts: x from 85 to 445, y from 130 to 930
blur_region(img_otp, (85, 130, 445, 930), radius=18)
img_otp.save('assets/tab_otp.jpg', quality=95)

# 3. Blur tab_discounts.png (chat conversation)
img_chat = Image.open('assets/tab_discounts.png')
# Header contact name: x from 75 to 380, y from 10 to 60
blur_region(img_chat, (75, 10, 380, 60), radius=18)
# All chat bubbles content: y from 140 to 880, x from 50 to 495
blur_region(img_chat, (50, 140, 495, 880), radius=22)
img_chat.save('assets/tab_discounts.png')

print("All 3 screenshots blurred successfully!")
