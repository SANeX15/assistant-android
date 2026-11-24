# Assistant (アシスタント)
a google assistant routines alternative — with a flavour of japanese.

## Description
i made this app for myself, to be honest. i wanted the old Google Assistant routines system paired with some of my own custom works like WhatsApp message automation, or execution of a series of tasks hands-free.

made using a mix of Claude Sonnet 5.5, Haiku 5.5, and mostly my own brain's knowledge. use of AI was strictly for the purpose of optimising the application, learning kotlin, adjusting UI because i am new to compose-based ui design. 

this app was made to help me automate my phone with commands, while also learning japanese to make the people around me feel weird just for fun.

## Requirements

| Requirement                 |                   Number                    |
|:----------------------------|:-------------------------------------------:|
| Android Version             |                    <= 8                     |
| Minimum Storage             | 100 MB (small model) / 1.5 GB (large model) |
| Stable Internet Connection* |     atleast 1MBps for downloading model     |

'*' = for setup only

## Features
- Opens apps by a certain syntax (given below).
- Supports adding routines (only supports opening apps and speech for now).
- Asks again if unclear of command.
- Acts as an assistant stand-in.
- Transliterates inputs and outputs in the kanji and romaji.
- _more features coming soon..._

## Components
- Vosk (japanese models only) as **Speech-to-Text**.
- Inbuilt **Text-to-Speech**.
- [wanakana-kt](https://github.com/esnaultdev/wanakana-kt) for **Transliteration**.
- Kotlin as the **main language**.

## Credits
- [esnault-dev](https://github.com/esnaultdev) & [WaniKani](https://github.com/wanikani) for the WanaKana Japanese transliterator library.
- [alphacep](https://github.com/alphacep) for the Vosk API library.
- Claude for their limited support in solving most of my burnout problems.
- myself, cuz i made it.

_i hate capitalisation, when using my own words_