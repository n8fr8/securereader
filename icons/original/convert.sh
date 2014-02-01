#!/bin/bash

#Requires ImageMagick to be installed.
#Some builds of ImageMagick on OSX have problems generating the images correctly.

#This script scales and creates images at the correct dpi level for Android.
#It gets placed in a folder called res/drawable/source_images/ in your #Android project along with all your svg files.
#When creating svg files set the image size to the size that you want your hdpi images to be.
#To use simply run the create_images script from its folder and it will generate images for all the svg files.

for f in *.svg;
do
	echo "Processing: $f"

	fout=${f/.svg}
	lang=""
	
	if [ -f drawable/$f ]; then
		echo "manually scaled file exists at drawable"
		convert -background none drawable/$f ../res/drawable/${f/.svg}.png
	fi

	if [[ $fout =~ .*"_farsi" ]]; then
		fout=${fout/"_farsi"}
		lang="-ar"
	fi

mkdir -p ../../app/res/drawable${lang}-xhdpi;
mkdir -p ../../app/res/drawable${lang}-hdpi;
mkdir -p ../../app/res/drawable${lang}-mdpi;
mkdir -p ../../app/res/drawable${lang}-ldpi;

convert -strip -background none $f ../../app/res/drawable${lang}-xhdpi/${fout}.png
convert -strip -background none $f -resize 75% ../../app/res/drawable${lang}-hdpi/${fout}.png
convert -strip -background none $f -resize 50% ../../app/res/drawable${lang}-mdpi/${fout}.png
convert -strip -background none $f -resize 37.5% ../../app/res/drawable${lang}-ldpi/${fout}.png

done
