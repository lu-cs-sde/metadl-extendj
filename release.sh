#!/bin/bash

if [ $# -lt "1" ]; then
    echo "Usage: $0 VERSION"
    exit 1
fi

VERSION=$1

echo "This will script will tag, build and upload jastaddj $VERSION" \
	"to jastadd.org/releases/jastaddj/$VERSION"

while true; do
	read -p "Proceed? " yn
	case $yn in 
		[Yy]* ) break;;
		[Nn]* ) exit;;
		* ) echo "Please answer yes or no.";;
	esac
done

echo "Bumping version strings..."
echo "version=$VERSION" > src/res/JastAddJ.properties
echo "version.short=$VERSION" >> src/res/JastAddJ.properties
git commit -m "Bumped version strings" src/res/JastAddJ.properties

echo "Tagging the release..."
git tag -a $VERSION -m "Version $VERSION"

echo "Creating source release..."
ant release

echo "Creating new directory at jastadd.org..."
ssh login.cs.lth.se "mkdir /cs/jastadd/releases/jastaddj/$VERSION"

echo "Uploading files to jastadd.org..."
scp jastaddj-$VERSION.zip login.cs.lth.se:/cs/jastadd/releases/jastaddj/$VERSION

echo "Setting group write permission for uploaded files..."
ssh login.cs.lth.se "chmod -R g+w /cs/jastadd/releases/jastaddj/$VERSION"

echo
echo "Check that it works!"
echo "--------------------"
echo "1. Browse to the website and check that everything looks okay."
echo "2. Update the web pages to include links to the new release."
echo "3. Push the changes to git."
