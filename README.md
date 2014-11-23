# SheepIt Render Farm Client

## Website

[SheepIt RenderFarm](https://www.sheepit-renderfarm.com)

## Overview

SheepIt Render Farm Client is an *Open Source* client for the distributed render farm "Sheep It".

This fork is aimed at improving standalone client interface (cli).

## Compilation

You need Java 1.7, OpenJDK or Oracle are supported.
You also need ant.
To create the jar file, simply type "ant".

## Usage

Once you have a jar file you look at the usage by doing "java -jar bin/sheepit-client.jar".
When you are doing your development you can use a mirror of the main site who is specially made for demo/dev, it is localted at **http://www-demo.sheepit-renderfarm.com**

## Configuration file

inspired by the fork of @schtibe

You can specify a configuration file with the options from the command line :

	java -jar bin/sheepit-client.jar -c settings
	java -jar bin/sheepit-client.jar --config settings

Inside the settings file (short aliases not supported) :

	login USERNAME
	password PASSWORD
	verbose
	cache-dir /tmp/
	compute-method CPU_GPU
	cores 2
	gpu CUDA_0
	server https://www.sheepit-renderfarm.com
	request-time 2:00-8:30,17:00-23:00

Note that all options are optional and you can chain config files & options like that :

	java -jar bin/sheepit-client.har -c mypasswd --verbose -c conf_GPU
