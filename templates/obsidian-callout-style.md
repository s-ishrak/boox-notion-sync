---
title: "{{ book.title }}"
aliases: ["Notes from {{ book.title }}"]
author: "{{ book.authors }}"
totalPages: {{ book.totalPages }}
totalAnnotations: {{ annotations | length }}
bookFormat: "{{ book.format }}"
exportedAt: "{{ book.exportedAt | date('dd MMM yyyy hh:mm:ss a') }}"
---
# {{ book.title }}
##### {{ book.authors }}

{%- set prevChapter = "" %}
{%- for annotation in annotations %}
{%- if annotation.chapter != prevChapter %}

## {{ annotation.chapter }}
{%- set prevChapter = annotation.chapter %}
{%- endif %}
### Page: {{ annotation.pageNumber }}{% if book.totalPages %} ({{ (annotation.pageNumber / book.totalPages * 100) | round(2) }}%){% endif %} @ {{ annotation.createdAt | date("dd MMM yyyy hh:mm:ss a") }}

{{ annotation.quote }}

{%- set calloutType = "quote" %}
{%- set calloutTitle = "Quotable/Concept/General Idea" %}
{%- if annotation.color == "#f0ff00" %}
{%- set calloutType = "quote" %}
{%- set calloutTitle = "Quotable/Concept/General Idea" %}
{%- elseif annotation.color == "#00aaff" %}
{%- set calloutType = "important" %}
{%- set calloutTitle = "Striking/Intense" %}
{%- elseif annotation.color == "#ffc1c3" %}
{%- set calloutType = "danger" %}
{%- set calloutTitle = "In Discord" %}
{%- elseif annotation.color == "#00b036" %}
{%- set calloutType = "question" %}
{%- set calloutTitle = "Thought Provoking" %}
{%- elseif annotation.color == "#008000" %}
{%- set calloutType = "sceptic" %}
{%- set calloutTitle = "Sceptic" %}
{%- elseif annotation.color == "#ffaa00" %}
{%- set calloutType = "warning" %}
{%- set calloutTitle = "Unsound" %}
{%- elseif annotation.color == "#ee00ff" %}
{%- set calloutType = "stylish" %}
{%- set calloutTitle = "Stylish" %}
{%- endif %}

> [!{{ calloutType }}] {{ calloutTitle }}{% if annotation.note %}{% set noteLen = annotation.note | length %}{% if noteLen <= 50 %}: {{ annotation.note }}{% else %}
> {{ annotation.note }}{% endif %}{% endif %}

{% endfor %}
