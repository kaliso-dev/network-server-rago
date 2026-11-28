"""The skins of the lobby NPCs, drawn for Rago (originals), one per colour of their category.

From the root of the repository:
    python3 tools/npc-skins/npc_skins.py bukkit/rago-lobby/src/main/resources/skins
writes practice, ranked, duels, arena, fun, shop, stats and quetes (.png, 64x64, with the second
layer). The same seed gives the same pixels; a PNG that changes is signed again by MineSkin at the
next start of the lobby.
"""
import os
import sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from skinlib import Skin

SKIN = {'S': '#D7A07A', 's': '#B98462', 'T': '#E6B790', 'M': '#9C5F4A', 'W': '#F4F4F4', 'K': '#1C1C1C'}
TAN = {'S': '#B57E58', 's': '#946243', 'T': '#C99068', 'M': '#7A4634', 'W': '#F4F4F4', 'K': '#1C1C1C'}


def head(sk, front, hair_rows=2, ear=True, back_neck=True, side_hair_cols=3):
    """The head: front from rows, the rest hair and skin."""
    sk.put('head', 'front', front)
    sk.put('head', 'top', ['HGHHhHGH', 'HHhHHHHh', 'hHHGHhHH', 'HHHHHHGH', 'GHhHHHHH', 'HHHHGhHH', 'HhHHHHHG', 'HHGHhHHH'])
    sk.fill('head', 'bottom', 's')
    right = []
    for r in range(8):
        if r < hair_rows:
            right.append('HHGHHhHH')
        else:
            row = 'H' * side_hair_cols + 'S' * (8 - side_hair_cols)
            if ear and r in (4, 5):
                row = row[:side_hair_cols] + 's' + row[side_hair_cols + 1:]
            right.append(row)
    right[-1] = right[-1].replace('S', 's')
    sk.put('head', 'right', right)
    sk.put('head', 'left', [r[::-1] for r in right])
    back = ['HHGHHhHH', 'HhHHGHHH', 'HHHhHHGH', 'GHHHHHhH', 'HHhHHGHH', 'HHHHhHHH', 'hHHGHHHh', 'ssssssss' if back_neck else 'HHHHHHHH']
    sk.put('head', 'back', back)


def limb(sk, part, rows, outer=None, front=None, back=None):
    """A limb: the same rows around it, top the first row, bottom the last; outer: its outside face."""
    sk.profile(part, rows)
    sk.fill(part, 'top', rows[0][0])
    sk.fill(part, 'bottom', rows[-1][0])
    side = 'right' if part[0] == 'r' else 'left'
    if outer:
        sk.put(part, side, outer)
    if front:
        sk.put(part, 'front', front)
    if back:
        sk.put(part, 'back', back)


def practice():
    """Practice (aqua): the coach, cap, tracksuit, whistle."""
    sk = Skin(dict(SKIN, H='#3B2A1E', h='#291C13', G='#5A4330', E='#2F6FA8',
                   A='#24A9BA', a='#167886', b='#63D2DD', w='#F0F0F0', Y='#CDD2D9', y='#8C939C',
                   P='#1F2B45', p='#141C2F', O='#EEEEEE', o='#9CA2A8', Z='#D5E9EC'), seed=11)
    head(sk, ['HHHHHHHH', 'HHHHHHHH', 'HTSSSSTH', 'ShhSShhS', 'SWESSEWS', 'SSSssSSS', 'SSsMMsSS', 'sSSSSSSs'])
    # the cap: crown, peak, a white button
    sk.put('head', 'top', ['AAAAAAAA', 'AAAAAAAA', 'AAAAAAAA', 'AAAwwAAA', 'AAAwwAAA', 'AAAAAAAA', 'AAAAAAAA', 'AAAAAAAA'], overlay=True)
    sk.put('head', 'front', ['AAAAAAAA', 'AAAbbAAA', 'aaaaaaaa', '........', '........', '........', '........', '........'], overlay=True)
    side = ['AAAAAAAA', 'AAAAAAAA', '.aaaaaaa', '........', '........', '........', '........', '........']
    sk.put('head', 'right', side, overlay=True)
    sk.put('head', 'left', [r[::-1] for r in side], overlay=True)
    sk.put('head', 'back', ['AAAAAAAA', 'AAAAAAAA', 'aaawwaaa', '........', '........', '........', '........', '........'], overlay=True)
    # the jacket, zipped, the whistle on its cord
    sk.profile('body', ['A'] * 9 + ['a', 'P', 'P'])
    sk.put('body', 'front', ['AwwSSwwA', 'AAwSSwAA', 'AAAwwAAA', 'AAAYYAAA', 'AAAyyAAA', 'AAAZAAAA', 'AAAZAAAA',
                             'AAAZAAAA', 'AAAZAAAA', 'aaaZaaaa', 'PPPPPPPP', 'pPPPPPPp'])
    sk.put('body', 'back', ['AwwwwwwA', 'AAAAAAAA', 'AwwwwwwA', 'AAAAAAAA', 'AAAAAAAA', 'AAAAAAAA', 'AAAAAAAA',
                            'AAAAAAAA', 'AAAAAAAA', 'aaaaaaaa', 'PPPPPPPP', 'pPPPPPPp'])
    sk.fill('body', 'top', 'A')
    sk.fill('body', 'bottom', 'P')
    sleeve = ['A'] * 8 + ['a', 'S', 'S', 's']
    stripe = ['AwwA'] * 8 + ['aaaa', 'SSSS', 'SSSS', 'ssss']
    limb(sk, 'rarm', sleeve, outer=stripe)
    limb(sk, 'larm', sleeve, outer=stripe)
    leg = ['P'] * 9 + ['O', 'O', 'o']
    leg_out = ['PAAP'] * 9 + ['OOOO', 'OAAO', 'oooo']
    limb(sk, 'rleg', leg, outer=leg_out, front=['PPPP'] * 9 + ['OOOO', 'OOAA', 'oooo'])
    limb(sk, 'lleg', leg, outer=leg_out, front=['PPPP'] * 9 + ['OOOO', 'AAOO', 'oooo'])
    return sk


def ranked():
    """RBW Modes (gold): the Bed Wars veteran, dyed leather on top, diamond below, the bed on the chest."""
    sk = Skin(dict(SKIN, H='#1E1A18', h='#100E0D', G='#3A332E', E='#5B3A1E', c='#A87A5C',
                   A='#D99A1E', a='#A56E10', b='#F3C24F', q='#7E540C', D='#4ED3CD', d='#2A9A96', e='#9DEDE9',
                   r='#9E2A2A', w='#F2F2F2', u='#6B4A2A'), seed=22)
    head(sk, ['HHHHHHHH', 'HHHHHHHH', 'HHTSSTHH', 'ShhSShhS', 'SWESSEWS', 'SSSssSSS', 'cSsMMsSc', 'sccccccs'])
    # the leather cap of Bed Wars, with its stitched edge
    sk.put('head', 'top', ['AAAAAAAA', 'AbAAAAbA', 'AAAAAAAA', 'AAAqqAAA', 'AAAqqAAA', 'AAAAAAAA', 'AbAAAAbA', 'AAAAAAAA'], overlay=True)
    sk.put('head', 'front', ['AAAAAAAA', 'qqqqqqqq', '........', '........', '........', '........', '........', '........'], overlay=True)
    side = ['AAAAAAAA', 'AAAAAAAA', 'qqqqqqqq', '........', '........', '........', '........', '........']
    sk.put('head', 'right', side, overlay=True)
    sk.put('head', 'left', [r[::-1] for r in side], overlay=True)
    sk.put('head', 'back', ['AAAAAAAA', 'AAAAAAAA', 'AAAAAAAA', 'qqqqqqqq', '........', '........', '........', '........'], overlay=True)
    # the leather chestplate with its seams, the bed of Bed Wars on the chest, diamond leggings below
    sk.profile('body', ['A'] * 10 + ['D', 'D'])
    sk.put('body', 'front', ['AqSSSSqA', 'AAqSSqAA', 'AAAqqAAA', 'AbAAAAbA', 'AAwrrrAA', 'AAuuuuAA', 'AAAAAAAA',
                             'AbAAAAbA', 'AAAAAAAA', 'qqqqqqqq', 'DdDDDDdD', 'dDDeeDDd'])
    sk.put('body', 'back', ['AqqqqqqA', 'AAAAAAAA', 'AAAAAAAA', 'AAAqqAAA', 'AAAqqAAA', 'AAAAAAAA', 'AAAAAAAA',
                            'AAAAAAAA', 'AAAAAAAA', 'qqqqqqqq', 'DDDDDDDD', 'dDDDDDDd'])
    sk.fill('body', 'top', 'A')
    sk.fill('body', 'bottom', 'D')
    sleeve = ['A', 'A', 'A', 'A', 'A', 'A', 'A', 'q', 'S', 'S', 'S', 's']
    limb(sk, 'rarm', sleeve, outer=['AAAA', 'AbbA', 'AAAA', 'AAAA', 'AAAA', 'AAAA', 'AAAA', 'qqqq', 'SSSS', 'SSSS', 'SSSS', 'ssss'])
    limb(sk, 'larm', sleeve, outer=['AAAA', 'AbbA', 'AAAA', 'AAAA', 'AAAA', 'AAAA', 'AAAA', 'qqqq', 'SSSS', 'SSSS', 'SSSS', 'ssss'])
    leg = ['D', 'D', 'D', 'D', 'D', 'D', 'd', 'D', 'D', 'D', 'D', 'd']
    leg_front = ['DDDD', 'DeDD', 'DDDD', 'DDDD', 'DDDD', 'dddd', 'DeDD', 'DDDD', 'dddd', 'DeeD', 'DDDD', 'dddd']
    limb(sk, 'rleg', leg, front=leg_front, outer=['DDDD', 'DDDD', 'DDeD', 'DDDD', 'DDDD', 'dddd', 'DDDD', 'DDDD', 'dddd', 'DDDD', 'DDDD', 'dddd'])
    limb(sk, 'lleg', leg, front=[r[::-1] for r in leg_front], outer=['DDDD', 'DDDD', 'DeDD', 'DDDD', 'DDDD', 'dddd', 'DDDD', 'DDDD', 'dddd', 'DDDD', 'DDDD', 'dddd'])
    return sk


def duels():
    """Duels (green): the duelist, green tunic, mail sleeves, crossed swords, belt and boots."""
    sk = Skin(dict(SKIN, H='#D8B04A', h='#AE8A2E', G='#EDD27A', E='#3F7A3A',
                   A='#3FA34D', a='#2A7435', b='#72C97C', I='#BCC1C8', i='#A2A8B1', B='#6A4326', U='#E7C04A',
                   P='#2C2D35', p='#1D1E24', O='#5B3B22', o='#3C2616', g='#4E3220'), seed=33)
    head(sk, ['HHHHHHHH', 'HGHHHHGH', 'HTSSSSTH', 'ShhSShhS', 'SWESSEWS', 'SSSssSSS', 'SSsMMsSS', 'sSSSSSSs'])
    # a green band in the hair, tied at the back
    sk.put('head', 'front', ['........', 'aAAAAAAa', '........', '........', '........', '........', '........', '........'], overlay=True)
    side = ['........', 'AAAAAAAA', '........', '........', '........', '........', '........', '........']
    sk.put('head', 'right', side, overlay=True)
    sk.put('head', 'left', side, overlay=True)
    sk.put('head', 'back', ['........', 'AAAaaAAA', '...aa...', '...a....', '........', '........', '........', '........'], overlay=True)
    sk.put('head', 'top', ['HHHHHHHH'] * 8, overlay=False)
    sk.put('head', 'top', ['GHHhHHGH', 'HhHHHGHH', 'HHGHhHHH', 'hHHHHHHG', 'HHHGHHhH', 'HGhHHHHH', 'HHHHhGHH', 'HhHHHHHh'])
    # the tunic over mail, crossed swords, the belt with its buckle
    sk.profile('body', ['A'] * 8 + ['B', 'A', 'P', 'P'])
    sk.put('body', 'front', ['IiSSSSiI', 'AAiSSiAA', 'AAAAAAAA', 'AIAAAAIA', 'AAIAAIAA', 'AAAIIAAA', 'AAAIIAAA',
                             'AAgAAgAA', 'BBBUUBBB', 'AaAAAAaA', 'PPPPPPPP', 'pPPPPPPp'])
    sk.put('body', 'back', ['IiiiiiiI', 'AAAAAAAA', 'AAAAAAAA', 'AAAAAAAA', 'AAAAAAAA', 'AAAAAAAA', 'AAAAAAAA',
                            'AAAAAAAA', 'BBBBBBBB', 'AaAAAAaA', 'PPPPPPPP', 'pPPPPPPp'])
    sk.fill('body', 'top', 'I')
    sk.fill('body', 'bottom', 'P')
    # green sleeves, the mail of the forearm under them
    sleeve = ['A', 'A', 'A', 'A', 'A', 'a', 'I', 'i', 'I', 'S', 'S', 's']
    limb(sk, 'rarm', sleeve, outer=['AAAA', 'AbAA', 'AAAA', 'AAAA', 'AAAA', 'aaaa', 'IIII', 'iIiI', 'IIII', 'SSSS', 'SSSS', 'ssss'])
    limb(sk, 'larm', sleeve, outer=['AAAA', 'AAbA', 'AAAA', 'AAAA', 'AAAA', 'aaaa', 'IIII', 'IiIi', 'IIII', 'SSSS', 'SSSS', 'ssss'])
    leg = ['P', 'P', 'P', 'P', 'P', 'P', 'O', 'O', 'O', 'O', 'O', 'o']
    boots = ['PPPP', 'PPPP', 'PPPP', 'PPPP', 'pPPp', 'PPPP', 'oooo', 'OOOO', 'OOOO', 'OOOO', 'OOOO', 'oooo']
    limb(sk, 'rleg', leg, front=boots, outer=boots)
    limb(sk, 'lleg', leg, front=boots, outer=boots)
    return sk


def arena():
    """Arenas: the gladiator, bronze helmet with a short crest, straps, bracers, greaves."""
    sk = Skin(dict(TAN, H='#2A1D14', h='#1A120C', G='#43301F', E='#4A3A2A', c='#8E5E40',
                   N='#B8873C', n='#7D5A24', m='#E2B35F', R='#A83A32', r='#7A2620', L='#7A4E2C', l='#523318'), seed=44)
    head(sk, ['HHHHHHHH', 'HHHHHHHH', 'HTSSSSTH', 'ShhSShhS', 'SWESSEWS', 'SSSssSSS', 'cSsMMsSc', 'sccccccs'])
    # the helmet: bronze bowl, cheek guards, nose guard, a short crest from front to back
    sk.put('head', 'top', ['NNNRRNNN', 'NmNRRNmN', 'NNNRRNNN', 'NNNrrNNN', 'NNNRRNNN', 'NNNRRNNN', 'NmNrrNmN', 'NNNRRNNN'], overlay=True)
    sk.put('head', 'front', ['NNNRRNNN', 'mNNNNNNm', 'nnnNNnnn', 'N..nn..N', 'N......N', 'n......n', '........', '........'], overlay=True)
    side = ['NNNNNNNN', 'NNmNNNNN', 'nnnnnnnn', 'NNNNN.NN', 'NNNN..NN', 'nnnn..nn', '........', '........']
    sk.put('head', 'right', side, overlay=True)
    sk.put('head', 'left', [r[::-1] for r in side], overlay=True)
    sk.put('head', 'back', ['NNNRRNNN', 'NNNRRNNN', 'nnnnnnnn', 'NNNNNNNN', 'NNNNNNNN', 'nnnnnnnn', '........', '........'], overlay=True)
    # a bare chest under a leather harness, the belt and the strips of the skirt
    sk.profile('body', ['S', 'S', 'S', 'S', 'S', 'S', 'S', 'S', 'L', 'L', 'l', 'L'])
    sk.put('body', 'front', ['LSSSSSSL', 'SLSSSSLS', 'SSLTTLSS', 'SSSNNSSS', 'SSLmNLSS', 'SLSssSLS', 'LSSSSSSL',
                             'SSsSSsSS', 'LLLNNLLL', 'LlLlLlLl', 'LlLlLlLl', 'lLlLlLlL'])
    sk.put('body', 'back', ['LSSSSSSL', 'SLSSSSLS', 'SSLSSLSS', 'SSSLLSSS', 'SSLSSLSS', 'SLSSSSLS', 'LSSSSSSL',
                            'SSSSSSSS', 'LLLLLLLL', 'LlLlLlLl', 'LlLlLlLl', 'lLlLlLlL'])
    sk.fill('body', 'top', 'S')
    sk.fill('body', 'bottom', 'l')
    # a bronze shoulder guard on the sword arm, leather bracers
    limb(sk, 'rarm', ['N', 'N', 'n', 'S', 'S', 'S', 'L', 'L', 'l', 'S', 'S', 's'],
         outer=['NmNN', 'NNNN', 'nnnn', 'SSSS', 'SSSS', 'SSSS', 'LNNL', 'LLLL', 'llll', 'SSSS', 'SSSS', 'ssss'])
    sk.fill('rarm', 'top', 'N')
    limb(sk, 'larm', ['S', 'S', 'S', 'S', 'S', 'S', 'L', 'L', 'l', 'S', 'S', 's'],
         outer=['SSSS', 'SSSS', 'SSSS', 'SSSS', 'SSSS', 'SSSS', 'LNNL', 'LLLL', 'llll', 'SSSS', 'SSSS', 'ssss'])
    leg = ['S', 'S', 'S', 'S', 'N', 'N', 'N', 'N', 'n', 'L', 'L', 'l']
    greave = ['SSSS', 'SSSS', 'SSSS', 'sSSs', 'NmNN', 'NNNN', 'NmNN', 'NNNN', 'nnnn', 'LSLS', 'LLLL', 'llll']
    limb(sk, 'rleg', leg, front=greave, outer=greave)
    limb(sk, 'lleg', leg, front=[r[::-1] for r in greave], outer=greave)
    return sk


def fun():
    """Fun Practice (light purple): the joker, hood up, a snowball on the chest, sneakers."""
    sk = Skin(dict(SKIN, H='#C9662B', h='#9C4C1E', G='#E08A4A', E='#6B3FA0', M='#6E2E3A',
                   A='#A957CF', a='#7C3A9D', b='#CB8AE6', w='#F4F4F4', v='#BFE3F5', J='#3E5F91', j='#2B4569',
                   O='#F4F4F4', o='#E07AB8', k='#2A2A2A', z='#E7DFF0'), seed=55)
    head(sk, ['HHHHHHHH', 'HGHHGHHH', 'HHSSSSHH', 'ShhSShhS', 'SWESSEWS', 'SSSssSSS', 'SMSSSSMS', 'sSMMMMSs'])
    # the hood, open on the face
    sk.put('head', 'top', ['AAAAAAAA', 'AbAAAAAA', 'AAAAAbAA', 'AAAAAAAA', 'AAbAAAAA', 'AAAAAAbA', 'AAAAAAAA', 'AAAAAAAA'], overlay=True)
    sk.put('head', 'front', ['aAAAAAAa', 'A......A', 'A......A', 'a......a', 'A......A', 'A......A', 'a......a', 'A......A'], overlay=True)
    side = ['AAAAAAAA', 'AAAAAbAA', 'AAAAAAAA', 'AbAAAAAA', 'AAAAAAAA', 'AAAAAAbA', 'AAAAAAAA', 'aaaaaaaa']
    sk.put('head', 'right', side, overlay=True)
    sk.put('head', 'left', [r[::-1] for r in side], overlay=True)
    sk.put('head', 'back', ['AAAAAAAA', 'AAbAAAAA', 'AAAAAAAA', 'AAAAAbAA', 'AAAAAAAA', 'AbAAAAAA', 'AAAAAAAA', 'aaaaaaaa'], overlay=True)
    # the hoodie: strings, a snowball, the pocket; jeans
    sk.profile('body', ['A'] * 10 + ['J', 'J'])
    sk.put('body', 'front', ['AaSSSSaA', 'AAwaawAA', 'AAwAAwAA', 'AwwvAAAA', 'wwwwvAAA', 'wwwvvAAA', 'AvvAAAAA',
                             'AaaaaaaA', 'AaAAAAaA', 'aaaaaaaa', 'JJJJJJJJ', 'jJJJJJJj'])
    sk.put('body', 'back', ['AaaaaaaA', 'AAAAAAAA', 'AAAAAAAA', 'AAAAAAAA', 'AAAbAAAA', 'AAAAAAAA', 'AAAAAAAA',
                            'AAAAAAAA', 'AAAAAAAA', 'aaaaaaaa', 'JJJJJJJJ', 'jJJJJJJj'])
    sk.fill('body', 'top', 'A')
    sk.fill('body', 'bottom', 'J')
    sleeve = ['A'] * 8 + ['a', 'S', 'S', 's']
    limb(sk, 'rarm', sleeve, outer=['AAAA', 'AAbA', 'AAAA', 'AAAA', 'AbAA', 'AAAA', 'AAAA', 'AAAA', 'aaaa', 'SSSS', 'SSSS', 'ssss'])
    limb(sk, 'larm', sleeve, outer=['AAAA', 'AbAA', 'AAAA', 'AAAA', 'AAbA', 'AAAA', 'AAAA', 'AAAA', 'aaaa', 'SSSS', 'SSSS', 'ssss'])
    leg = ['J'] * 9 + ['O', 'O', 'o']
    limb(sk, 'rleg', leg, front=['JJJJ', 'JJJJ', 'JJJJ', 'JJjJ', 'JJJJ', 'JJJJ', 'jJJJ', 'JJJJ', 'jjjj', 'OOOO', 'OooO', 'oooo'],
         outer=['JJJJ'] * 8 + ['jjjj', 'OOOO', 'oOOo', 'oooo'])
    limb(sk, 'lleg', leg, front=['JJJJ', 'JJJJ', 'JjJJ', 'JJJJ', 'JJJJ', 'JJJj', 'JJJJ', 'JJJJ', 'jjjj', 'OOOO', 'OooO', 'oooo'],
         outer=['JJJJ'] * 8 + ['jjjj', 'OOOO', 'oOOo', 'oooo'])
    return sk


def shop():
    """Shop (green): the merchant, flat cap, rolled sleeves, apron with an emerald."""
    sk = Skin(dict(SKIN, H='#5A3A24', h='#3E2717', G='#7A5236', E='#2F7A4A', u='#4A2F1D',
                   A='#2F8A57', a='#1F6440', b='#4FB57A', w='#EDEBE4', v='#C9C6BC', X='#47E08E', x='#1E9E55',
                   B='#6A4326', P='#4A3426', p='#33231A', O='#262626', o='#141414', C='#245C3C', c='#183F29'), seed=66)
    head(sk, ['HHHHHHHH', 'HHHHHHHH', 'HTSSSSTH', 'ShhSShhS', 'SWESSEWS', 'SSSssSSS', 'SuuuuuuS', 'sSSMMSSs'])
    # the flat cap
    sk.put('head', 'top', ['CCCCCCCC', 'CCCCCCCC', 'CCCcCCCC', 'CCCCCCCC', 'CCCCCCCC', 'CCCCcCCC', 'CCCCCCCC', 'CCCCCCCC'], overlay=True)
    sk.put('head', 'front', ['CCCCCCCC', 'cccccccc', '........', '........', '........', '........', '........', '........'], overlay=True)
    side = ['CCCCCCCC', 'CCCCCCcc', '........', '........', '........', '........', '........', '........']
    sk.put('head', 'right', side, overlay=True)
    sk.put('head', 'left', [r[::-1] for r in side], overlay=True)
    sk.put('head', 'back', ['CCCCCCCC', 'cccccccc', '........', '........', '........', '........', '........', '........'], overlay=True)
    # the shirt, the apron and its emerald, the ties of the apron
    sk.profile('body', ['w'] * 8 + ['B', 'P', 'P', 'P'])
    sk.put('body', 'front', ['wwASSAww', 'wwAwwAww', 'wwAwwAww', 'wAAAAAAw', 'wAbAAAAw', 'wAAXxAAw', 'wAAxXAAw',
                             'wAaaaaAw', 'BAAAAAAB', 'PAAAAAAP', 'PAbAAAAP', 'pAAAAAAp'])
    sk.put('body', 'back', ['wwwwwwww', 'wwAwwAww', 'wwwAAwww', 'wwAwwAww', 'wAwwwwAw', 'wwwwwwww', 'wwwwwwww',
                            'wwwwwwww', 'BBBAABBB', 'PPPAAPPP', 'PPPPPPPP', 'pPPPPPPp'])
    sk.fill('body', 'top', 'w')
    sk.fill('body', 'bottom', 'P')
    sleeve = ['w', 'w', 'w', 'w', 'v', 'S', 'S', 'S', 'S', 'S', 'S', 's']
    limb(sk, 'rarm', sleeve)
    limb(sk, 'larm', sleeve)
    leg = ['P'] * 9 + ['O', 'O', 'o']
    limb(sk, 'rleg', leg, front=['PAAA', 'PAAA', 'PAbA', 'PAAA', 'PAAA', 'Paaa', 'PPPP', 'PPPP', 'pPPp', 'OOOO', 'OOOO', 'oooo'])
    limb(sk, 'lleg', leg, front=['AAAP', 'AAAP', 'AAAP', 'AbAP', 'AAAP', 'aaaP', 'PPPP', 'PPPP', 'pPPp', 'OOOO', 'OOOO', 'oooo'])
    return sk


def stats():
    """Stats (gold): the analyst, navy suit, gold tie and glasses, grey hair."""
    sk = Skin(dict(SKIN, H='#9C9C9C', h='#6E6E6E', G='#C8C8C8', E='#3A5A7A',
                   N='#22305A', n='#172142', w='#EFEFEF', Y='#E3B23C', y='#B4861F', g='#D9A834',
                   P='#1B2547', p='#121935', K='#161616', U='#E3B23C', O='#1A1A1A', o='#0E0E0E'), seed=77)
    head(sk, ['HHHHHHHH', 'HGHHHHGH', 'HTSSSSTH', 'ShhSShhS', 'gWEggEWg', 'SSSssSSS', 'SSsMMsSS', 'sSSSSSSs'], side_hair_cols=2)
    sk.put('head', 'right', ['HHGHHhHH', 'HHHHHHHH', 'HHSSSSSS', 'HHSSSSgg', 'HHSsSSSS', 'HHSsSSSS', 'HHSSSSSS', 'HHssssss'])
    sk.put('head', 'left', ['HHhHHGHH', 'HHHHHHHH', 'SSSSSSHH', 'ggSSSSHH', 'SSSSsSHH', 'SSSSsSHH', 'SSSSSSHH', 'ssssssHH'])
    # the suit: lapels, white shirt, gold tie, a gold pocket square, the belt
    sk.profile('body', ['N'] * 10 + ['K', 'P'])
    sk.put('body', 'front', ['NwwYYwwN', 'NnwYYwnN', 'NNnYYnNN', 'NNNyYNgN', 'NNNYyNNN', 'NNNyYNNN', 'NNNNNNNN',
                             'NNNnNNNN', 'NNNNNNNN', 'NNNnNNNN', 'KKKUKKKK', 'PPPPPPPP'])
    sk.put('body', 'back', ['NwwwwwwN', 'NNNNNNNN', 'NNNNNNNN', 'NNNNNNNN', 'NNNNNNNN', 'NNNNNNNN', 'NNNNNNNN',
                            'NNNnnNNN', 'NNNnnNNN', 'NNNnnNNN', 'KKKKKKKK', 'PPPPPPPP'])
    sk.fill('body', 'top', 'N')
    sk.fill('body', 'bottom', 'P')
    sleeve = ['N'] * 8 + ['w', 'S', 'S', 's']
    limb(sk, 'rarm', sleeve, front=['NNNN'] * 6 + ['NgNN', 'NNNN', 'wwww', 'SSSS', 'SSSS', 'ssss'])
    limb(sk, 'larm', sleeve, front=['NNNN'] * 6 + ['NNgN', 'NNNN', 'wwww', 'SSSS', 'SSSS', 'ssss'])
    leg = ['P'] * 10 + ['O', 'o']
    limb(sk, 'rleg', leg, front=['PPPP', 'PPPP', 'PPPp', 'PPPP', 'PPPP', 'PPPp', 'PPPP', 'PPPP', 'PPPp', 'PPPP', 'OOOO', 'oooo'])
    limb(sk, 'lleg', leg, front=['PPPP', 'PPPP', 'pPPP', 'PPPP', 'PPPP', 'pPPP', 'PPPP', 'PPPP', 'pPPP', 'PPPP', 'OOOO', 'oooo'])
    return sk


def quests():
    """Quests (gold): the explorer, wide hat, leather jacket, satchel strap, a gold compass."""
    sk = Skin(dict(SKIN, H='#6B4A2E', h='#4A321E', G='#8A6440', E='#3F6A4A', c='#A87A5C',
                   F='#7A5432', f='#5A3C22', R='#3C7A66', r='#2A5A4A', w='#E9DFC6', L='#6E4426', l='#4E2F19',
                   Y='#E3B23C', y='#A97E1C', P='#B49B6E', p='#8C774F', O='#4A2F1C', o='#2E1D11', B='#3A2414'), seed=88)
    head(sk, ['HHHHHHHH', 'HHHHHHHH', 'HTSSSSTH', 'ShhSShhS', 'SWESSEWS', 'SSSssSSS', 'cSsMMsSc', 'sccccccs'])
    # the hat: crown and brim, a green band
    sk.put('head', 'top', ['FFFFFFFF', 'FFFFFFFF', 'FFFfFFFF', 'FFffFFFF', 'FFFFffFF', 'FFFFfFFF', 'FFFFFFFF', 'FFFFFFFF'], overlay=True)
    sk.put('head', 'front', ['FFFFFFFF', 'RRRRRRRR', 'ffffffff', '........', '........', '........', '........', '........'], overlay=True)
    side = ['FFFFFFFF', 'RRRRRRRR', 'ffffffff', '........', '........', '........', '........', '........']
    sk.put('head', 'right', side, overlay=True)
    sk.put('head', 'left', side, overlay=True)
    sk.put('head', 'back', side, overlay=True)
    # the shirt under an open leather jacket, a green scarf, the strap of the satchel, the compass
    sk.profile('body', ['L'] * 9 + ['l', 'P', 'P'])
    sk.put('body', 'front', ['LRRRRRRL', 'LLRwrRLL', 'lLwwwwLl', 'lLlwwwLL', 'LLwlwwLL', 'LLwwlwLL', 'LLwYywLL',
                             'LLwyYlLL', 'LLwwwwlL', 'llBBBBll', 'PPPPPPPP', 'pPPPPPPp'])
    sk.put('body', 'back', ['LRRRRRRL', 'LLLLLLlL', 'LLLLLlLL', 'LLLLlLLL', 'LLLlLLLL', 'LLlLLLLL', 'LlLLLLLL',
                            'lLLLLLLL', 'LLLLLLLL', 'llllllll', 'PPPPPPPP', 'pPPPPPPp'])
    sk.fill('body', 'top', 'L')
    sk.fill('body', 'bottom', 'P')
    sleeve = ['L'] * 8 + ['l', 'S', 'S', 's']
    limb(sk, 'rarm', sleeve, outer=['LLLL', 'LLLL', 'LlLL', 'LLLL', 'LLLL', 'LLlL', 'LLLL', 'LLLL', 'llll', 'SSSS', 'SSSS', 'ssss'])
    limb(sk, 'larm', sleeve, outer=['LLLL', 'LLLL', 'LLlL', 'LLLL', 'LLLL', 'LlLL', 'LLLL', 'LLLL', 'llll', 'SSSS', 'SSSS', 'ssss'])
    leg = ['P'] * 7 + ['O', 'O', 'O', 'O', 'o']
    boots = ['PPPP', 'PPPP', 'PPpP', 'PPPP', 'PPPP', 'pPPP', 'PPPP', 'oooo', 'OOOO', 'OOOO', 'OOOO', 'oooo']
    limb(sk, 'rleg', leg, front=boots, outer=boots)
    limb(sk, 'lleg', leg, front=[r[::-1] for r in boots], outer=boots)
    return sk


if __name__ == '__main__':
    out = sys.argv[1]
    for name, make in (('practice', practice), ('ranked', ranked), ('duels', duels), ('arena', arena), ('fun', fun),
                       ('shop', shop), ('stats', stats), ('quetes', quests)):
        make().save(os.path.join(out, name + '.png'))
        print(name)
